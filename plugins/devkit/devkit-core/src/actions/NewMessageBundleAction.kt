// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions

import com.intellij.ide.actions.CreateElementActionBase
import com.intellij.ide.actions.CreateTemplateInPackageAction
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.xml.DomManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.util.function.Consumer
import java.util.function.Predicate

class NewMessageBundleAction : CreateElementActionBase(), UpdateInBackground {
  override fun invokeDialog(project: Project, directory: PsiDirectory, elementsConsumer: Consumer<in Array<PsiElement>>) {
    val module = ModuleUtilCore.findModuleForPsiElement(directory) ?: return
    if (module.name.endsWith(".impl") && ModuleManager.getInstance(project).findModuleByName(module.name.removeSuffix(".impl")) != null) {
      Messages.showErrorDialog(project, DevKitBundle.message(
        "action.DevKit.NewMessageBundle.error.message.do.not.put.bundle.to.impl.module"), errorTitle)
      return
    }

    val validator = MyInputValidator(project, directory)
    val defaultName = generateDefaultBundleName(module)
    val result = Messages.showInputDialog(project, DevKitBundle.message("action.DevKit.NewMessageBundle.label.bundle.name"),
                                          DevKitBundle.message("action.DevKit.NewMessageBundle.title.create.new.message.bundle"), null,
                                          defaultName, validator)
    if (result != null) {
      elementsConsumer.accept(validator.createdElements)
    }
  }

  override fun create(newName: String, directory: PsiDirectory): Array<PsiElement> {
    val bundleClass = DevkitActionsUtil.createSingleClass(newName, "MessageBundle.java", directory)
    val module = ModuleUtilCore.findModuleForPsiElement(directory) ?: return emptyArray()
    val pluginXml = PluginModuleType.getPluginXml(module)
    if (pluginXml != null) {
      DescriptorUtil.patchPluginXml({ xmlFile, psiClass ->
                                      val fileElement = DomManager.getDomManager(module.project).getFileElement(xmlFile,
                                                                                                                IdeaPlugin::class.java)
                                      if (fileElement != null) {
                                        val resourceBundle = fileElement.rootElement.resourceBundle
                                        if (!resourceBundle.exists()) {
                                          resourceBundle.value = "messages.$newName"
                                        }
                                      }
                                    }, bundleClass, pluginXml)
    }
    val resourcesRoot = getOrCreateResourcesRoot(module)
    if (resourcesRoot == null) return arrayOf(bundleClass)
    val propertiesFile = runWriteAction {
      val messagesDirName = "messages"
      val messagesDir = resourcesRoot.findSubdirectory(messagesDirName) ?: resourcesRoot.createSubdirectory(messagesDirName)
      messagesDir.createFile("$newName.properties")
    }
    return arrayOf(bundleClass, propertiesFile)
  }

  private fun getOrCreateResourcesRoot(module: Module): PsiDirectory? {
    fun reportError(@Nls message: String): Nothing? {
      val notification =
        Notification("DevKit Errors",
                     DevKitBundle.message("action.DevKit.NewMessageBundle.notification.title.cannot.create.resources.root.for.properties.file"),
                     DevKitBundle.message("action.DevKit.NewMessageBundle.notification.content.cannot.create.resources.root.for.properties.file",
                                          message),
                     NotificationType.ERROR)
      Notifications.Bus.notify(notification, module.project)
      return null
    }
    fun createResourcesRoot(): VirtualFile? {
      val contentRoot = ModuleRootManager.getInstance(module).contentRoots.singleOrNull()
                        ?: return reportError(DevKitBundle.message("action.DevKit.NewMessageBundle.error.message.multiple.content.roots.for.module", module.name))
      @NonNls val resourcesDirName = "resources"
      if (contentRoot.findChild(resourcesDirName) != null) {
        return reportError(DevKitBundle.message("action.DevKit.NewMessageBundle.error.message.folder.already.exists",
                                                resourcesDirName, contentRoot.path))
      }
      if (ProjectFileIndex.getInstance(module.project).isInSource(contentRoot)) {
        return reportError(DevKitBundle.message("action.DevKit.NewMessageBundle.error.message.under.sources.root", contentRoot.path))
      }
      return runWriteAction {
        val resourcesDir = contentRoot.createChildDirectory(this, resourcesDirName)
        ModuleRootModificationUtil.updateModel(module) {
          it.contentEntries.single().addSourceFolder(resourcesDir, JavaResourceRootType.RESOURCE)
        }
        resourcesDir
      }
    }

    val resourcesRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE).firstOrNull()
                        ?: createResourcesRoot()
                        ?: return null
    return PsiManager.getInstance(module.project).findDirectory(resourcesRoot)
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    if (!super.isAvailable(dataContext)) {
      return false
    }
    if (!PsiUtil.isIdeaProject(dataContext.getData(CommonDataKeys.PROJECT))) return false

    return CreateTemplateInPackageAction.isAvailable(dataContext, JavaModuleSourceRootTypes.SOURCES,
                                                     Predicate { JavaCreateTemplateInPackageAction.doCheckPackageExists(it) })
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getErrorTitle(): String {
    return DevKitBundle.message("action.DevKit.NewMessageBundle.error.title.cannot.create.new.message.bundle")
  }

  override fun getActionName(directory: PsiDirectory, newName: String): String {
    return DevKitBundle.message("action.DevKit.NewMessageBundle.action.name.create.new.message.bundle", newName)
  }
}

@Suppress("HardCodedStringLiteral")
internal fun generateDefaultBundleName(module: Module): String {
  val nameWithoutPrefix = module.name.removePrefix("intellij.").removeSuffix(".impl")
  val commonGroupNames = listOf("platform", "vcs", "tools", "clouds")
  val commonPrefix = commonGroupNames.find { nameWithoutPrefix.startsWith("$it.") }
  val shortenedName = if (commonPrefix != null) nameWithoutPrefix.removePrefix("$commonPrefix.") else nameWithoutPrefix
  return shortenedName.split(".").joinToString("") { StringUtil.capitalize(it) } + "Bundle"
}
