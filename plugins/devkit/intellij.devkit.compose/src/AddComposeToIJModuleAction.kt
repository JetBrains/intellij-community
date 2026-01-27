// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose

import com.intellij.facet.FacetManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.initializeIfNeeded
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments

private const val JETBRAINS_KOTLIN_COMPOSE_COMPILER_JPS_LIB = "jetbrains.kotlin.compose.compiler.plugin"
private const val INTELLIJ_PLATFORM_COMPOSE_MODULE = "intellij.platform.compose"

internal class AddComposeToIJModuleAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY)
    e.presentation.isEnabledAndVisible = project != null &&
                                         isIntelliJPlatformProject(project) &&
                                         !modules.isNullOrEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) ?: return

    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      .getLibraryByName(JETBRAINS_KOTLIN_COMPOSE_COMPILER_JPS_LIB)
    if (library == null) {
      notifyError(project, DevkitComposeBundle.message("action.AddComposeToIJModule.error.library.not.found"))
      return
    }

    val kotlinComposePluginJarPath = library.getUrls(OrderRootType.CLASSES)
      .firstOrNull()
      ?.let { VfsUtil.urlToPath(it) }
      ?.removeSuffix("!/")

    if (kotlinComposePluginJarPath == null) {
      notifyError(project, DevkitComposeBundle.message("action.AddComposeToIJModule.error.library.not.found"))
      return
    }

    val composeModule = ModuleManager.getInstance(project).findModuleByName(INTELLIJ_PLATFORM_COMPOSE_MODULE)
    if (composeModule == null) {
      notifyError(project, DevkitComposeBundle.message("action.AddComposeToIJModule.error.module.not.found"))
      return
    }

    e.coroutineScope.launch {
      val successModules = mutableListOf<String>()
      val alreadySetModules = mutableListOf<String>()

      withBackgroundProgress(project, DevkitComposeBundle.message("action.AddComposeToIJModule.progress")) {
        writeCommandAction(project, DevkitComposeBundle.message("action.AddComposeToIJModule.text")) {
          for (module in modules) {
            if (isComposeSupportEnabled(module, composeModule, kotlinComposePluginJarPath)) {
              alreadySetModules.add(module.name)
            }
            else {
              if (addComposeSupport(module, composeModule, kotlinComposePluginJarPath)) {
                successModules.add(module.name)
              }
            }
          }
        }
      }

      if (successModules.isNotEmpty()) {
        val message = if (alreadySetModules.isEmpty())
          DevkitComposeBundle.message("action.AddComposeToIJModule.success", successModules.joinToString(", "))
        else
          DevkitComposeBundle.message("action.AddComposeToIJModule.partial.success",
                                      successModules.joinToString(", "),
                                      alreadySetModules.joinToString(", "))

        notifySuccess(project, message)
      }
      else if (alreadySetModules.isNotEmpty()) {
        val message = if (alreadySetModules.size == 1)
          DevkitComposeBundle.message("action.AddComposeToIJModule.already.set.module", alreadySetModules.first())
        else
          DevkitComposeBundle.message("action.AddComposeToIJModule.already.set")

        notifySuccess(project, message)
      }
    }
  }
}

private fun isComposeSupportEnabled(module: Module, composeModule: Module, kotlinComposePluginJarPath: String): Boolean {
  // 1. Module *.iml dependency
  val hasModuleDep = ModuleRootManager.getInstance(module).orderEntries.any {
    it is ModuleOrderEntry && it.module == composeModule
  }
  if (!hasModuleDep) return false

  // 2. XML dependency
  if (!isXmlPlatformComposePresent(module)) return false

  // 3. Kotlin facet
  val facet = FacetManager.getInstance(module).getFacetByType(KotlinFacetType.TYPE_ID) ?: return false
  val settings = facet.configuration.settings
  val args = settings.compilerSettings?.additionalArguments ?: ""
  if (!args.contains(COMPOSE_HOT_RELOAD_ENABLED_MARKER)) return false

  val currentPaths = settings.compilerArguments?.pluginClasspaths ?: emptyArray()
  return currentPaths.contains(kotlinComposePluginJarPath)
}

private fun isXmlPlatformComposePresent(module: Module): Boolean {
  val rootTag = findXmlDescriptor(module)?.rootTag ?: return true
  if (rootTag.name != "idea-plugin") return true

  val dependenciesTag = rootTag.findSubTags("dependencies").firstOrNull() ?: return false
  return dependenciesTag.findSubTags("module")
    .any { it.getAttributeValue("name") == INTELLIJ_PLATFORM_COMPOSE_MODULE }
}

private fun addComposeSupport(module: Module, composeModule: Module, kotlinComposePluginJarPath: String): Boolean {
  // 1. Add intellij.platform.compose dependency to module
  ModuleRootModificationUtil.addDependency(module, composeModule)

  // 2. Add intellij.platform.compose dependency to XML descriptor
  addPlatformComposeModuleDependency(module)

  // 3. Configure Kotlin facet
  val facetManager = FacetManager.getInstance(module)
  val model = FacetManager.getInstance(module).createModifiableModel()

  val facet = facetManager.getFacetByType(KotlinFacetType.TYPE_ID) ?: run {
    val newFacet = FacetManager.getInstance(module)
      .createFacet(KotlinFacetType.INSTANCE, KotlinFacetType.NAME, KotlinFacetType.INSTANCE.createDefaultConfiguration(), null)
    model.addFacet(newFacet)
    newFacet
  }

  val configuration = facet.configuration
  val settings = configuration.settings

  settings.initializeIfNeeded(module, null)
  settings.useProjectSettings = false

  // Enable Compose Hot Reload
  val compilerSettings = settings.compilerSettings
                         ?: CompilerSettings().also { settings.compilerSettings = it }
  val currentArgs = compilerSettings.additionalArguments
  if (!currentArgs.contains(COMPOSE_HOT_RELOAD_ENABLED_MARKER)) {
    compilerSettings.additionalArguments =
      if (currentArgs.isBlank()) "-P $COMPOSE_HOT_RELOAD_ENABLED_MARKER" else "$currentArgs -P $COMPOSE_HOT_RELOAD_ENABLED_MARKER"
  }

  // Enable kotlin-compose-compiler-plugin
  settings.updateCompilerArguments {
    val currentPaths = pluginClasspaths ?: emptyArray()
    if (!currentPaths.contains(kotlinComposePluginJarPath)) {
      pluginClasspaths = currentPaths + kotlinComposePluginJarPath
    }
  }

  model.commit()

  return true
}

private fun addPlatformComposeModuleDependency(module: Module) {
  val xmlFile = findXmlDescriptor(module) ?: return
  val rootTag = xmlFile.rootTag ?: return
  if (rootTag.name != "idea-plugin") return

  val dependenciesTag = (rootTag.findSubTags("dependencies").firstOrNull()
                         ?: rootTag.addSubTag(rootTag.createChildTag("dependencies", "", null, false), true))
                        ?: return

  val alreadyConfigured = dependenciesTag.findSubTags("module")
    .any { it.getAttributeValue("name") == INTELLIJ_PLATFORM_COMPOSE_MODULE }
  if (!alreadyConfigured) {
    val moduleTag = dependenciesTag.createChildTag("module", "", null, false)
    moduleTag.setAttribute("name", INTELLIJ_PLATFORM_COMPOSE_MODULE)
    dependenciesTag.addSubTag(moduleTag, false)
  }
}

private fun findXmlDescriptor(module: Module): XmlFile? {
  val project = module.project
  val psiManager = PsiManager.getInstance(project)
  for (sourceRoot in ModuleRootManager.getInstance(module).getSourceRoots(false)) {
    val moduleXml = sourceRoot.findChild("${module.name}.xml")
    if (moduleXml != null && !moduleXml.isDirectory) {
      val psiFile = psiManager.findFile(moduleXml)
      if (psiFile is XmlFile) return psiFile
    }

    val metaInf = sourceRoot.findChild("META-INF")
    if (metaInf != null && metaInf.isDirectory) {
      for (file in metaInf.children) {
        if (file.name == "plugin.xml") {
          val psiFile = psiManager.findFile(file)
          if (psiFile is XmlFile) return psiFile
        }
      }
    }
  }
  return null
}

private fun notifySuccess(project: Project, content: @Nls String) {
  NotificationGroupManager.getInstance()
    .getNotificationGroup("DevKit Errors")
    .createNotification(content, NotificationType.INFORMATION)
    .notify(project)
}

private fun notifyError(project: Project, @Nls content: String) {
  NotificationGroupManager.getInstance()
    .getNotificationGroup("DevKit Errors")
    .createNotification(content, NotificationType.ERROR)
    .notify(project)
}