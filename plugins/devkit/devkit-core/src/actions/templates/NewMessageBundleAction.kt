// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.actions.CustomCreateFromTemplateAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.obsolete.getOrCreateBundleResourcesRoot
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType

internal class NewMessageBundleAction : CustomCreateFromTemplateAction("DevKit MessageBundle") {
  override fun customizeBuilder(builder: CreateFileFromTemplateDialog.Builder, directory: PsiDirectory) {
    val module = ModuleUtilCore.findModuleForPsiElement(directory)

    builder.setDefaultText(module?.let { generateDefaultBundleName(it) } ?: "MyMessageBundle")
    builder.setTitle(DevKitBundle.message("action.create.message.bundle.title"))
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    return super.isAvailable(dataContext) && isDevKitClassTemplateAvailable(dataContext)
  }

  override fun postProcess(
    createdElement: PsiFile, dataContext: DataContext,
    templateName: String?, customProperties: Map<String, String>?,
  ) {
    super.postProcess(createdElement, dataContext, templateName, customProperties)

    runWriteCommandAction(createdElement.project, DevKitBundle.message("command.create.bundle.properties"), null, {
      val module = dataContext.getData(PlatformCoreDataKeys.MODULE)
      if (module == null || !PsiUtil.isPluginModule(module)) {
        return@runWriteCommandAction
      }

      val resourcesRoot = getOrCreateBundleResourcesRoot(module) ?: return@runWriteCommandAction

      val messagesDirName = "messages"
      val messagesDir = resourcesRoot.findSubdirectory(messagesDirName) ?: resourcesRoot.createSubdirectory(messagesDirName)
      val newName = FileUtil.getNameWithoutExtension(createdElement.name)

      val propertiesName = "$newName.properties"
      if (messagesDir.findFile(propertiesName) == null) {
        messagesDir.createFile(propertiesName)
      }

      val classes = createdElement.toUElementOfType<UFile>()?.classes
      val bundleClass = classes?.firstOrNull()?.javaPsi

      val pluginXml = PluginModuleType.getPluginXml(module)
      if (pluginXml != null && bundleClass != null) {
        DescriptorUtil.patchPluginXml(
          { xmlFile, psiClass ->
            val fileElement = DomManager.getDomManager(module.project).getFileElement(xmlFile, IdeaPlugin::class.java)
            if (fileElement != null) {
              val resourceBundle = fileElement.rootElement.resourceBundle
              if (!resourceBundle.exists()) {
                resourceBundle.value = "messages.$newName"
              }
            }
          }, bundleClass, pluginXml)
      }
    })
  }
}

fun generateDefaultBundleName(module: Module): String {
  val nameWithoutPrefix = module.name.removePrefix("intellij.").removeSuffix(".impl")
  val commonGroupNames = listOf("platform", "vcs", "tools", "clouds")
  val commonPrefix = commonGroupNames.find { nameWithoutPrefix.startsWith("$it.") }
  val shortenedName = if (commonPrefix != null) nameWithoutPrefix.removePrefix("$commonPrefix.") else nameWithoutPrefix
  return shortenedName.split(".").joinToString("") { StringUtil.capitalize(it) } + "Bundle"
}