// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType

private const val DEVKIT_APPLICATION_STATE_TEMPLATE = "DevKit Application State"
private const val DEVKIT_PROJECT_STATE_TEMPLATE = "DevKit Project State"

internal class NewPersistentStateAction : CreateFileFromTemplateAction() {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder
      .setTitle(DevKitBundle.message("dialog.title.new.devkit.state"))
      .addKind(DevKitBundle.message("list.item.applications.state"), DevkitCoreIcons.State, DEVKIT_APPLICATION_STATE_TEMPLATE)
      .addKind(DevKitBundle.message("list.item.project.state"), DevkitCoreIcons.ProjectState, DEVKIT_PROJECT_STATE_TEMPLATE)
      .setDefaultText("MySettingsService")
  }

  override fun getActionName(directory: PsiDirectory?, newName: @NonNls String, templateName: @NonNls String?): @NlsContexts.Command String? {
    return DevKitBundle.message("action.name.new.state.service")
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    return super.isAvailable(dataContext) && isDevKitClassTemplateAvailable(dataContext)
  }

  override fun postProcess(createdElement: PsiFile, dataContext: DataContext, templateName: String, customProperties: Map<String, String>?) {
    super.postProcess(createdElement, dataContext, templateName, customProperties)

    patchPluginXml(createdElement, templateName)
  }

  private fun patchPluginXml(file: PsiFile, templateName: String) {
    val pluginXml = DevkitActionsUtil.choosePluginModuleDescriptor(file.parent!!) ?: return
    DescriptorUtil.checkPluginXmlsWritable(file.project, pluginXml)

    val tagName = when (templateName) {
      DEVKIT_APPLICATION_STATE_TEMPLATE -> "applicationSettings"
      DEVKIT_PROJECT_STATE_TEMPLATE -> "projectSettings"
      else -> throw IllegalArgumentException("Unknown template name: $templateName")
    }

    @Suppress("DialogTitleCapitalization")
    runWriteCommandAction(file.project, DevKitBundle.message("command.register.state.settings"), null, {
      val topLevelClasses = file.toUElementOfType<UFile>()?.classes
                              ?.takeIf { it.isNotEmpty() }
                            ?: return@runWriteCommandAction

      val rootTag = pluginXml.getDocument()?.rootTag ?: return@runWriteCommandAction

      if (IdeaPlugin.TAG_NAME == rootTag.name) {
        val extensionsTag = rootTag.findFirstSubTag("extensions") ?: return@runWriteCommandAction

        val newTag = extensionsTag.addSubTag(extensionsTag.createChildTag(tagName, extensionsTag.namespace, null, false), false)
        newTag.setAttribute("service", topLevelClasses.first().qualifiedName)
      }
    }, pluginXml)
  }
}