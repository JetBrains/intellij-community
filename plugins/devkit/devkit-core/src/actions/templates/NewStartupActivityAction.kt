// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.icons.AllIcons
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

private const val DEVKIT_IMMEDIATE_ACTIVITY_TEMPLATE = "DevKit Project Activity"
private const val DEVKIT_BACKGROUND_STARTUP_ACTIVITY_TEMPLATE = "DevKit Background Project Activity"

internal class NewStartupActivityAction : CreateFileFromTemplateAction() {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder
      .setTitle(DevKitBundle.message("dialog.title.new.devkit.startup.activity"))
      .addKind(DevKitBundle.message("list.item.background.activity"), AllIcons.RunConfigurations.TestState.Run, DEVKIT_BACKGROUND_STARTUP_ACTIVITY_TEMPLATE)
      .addKind(DevKitBundle.message("list.item.immediate.activity"), AllIcons.RunConfigurations.TestState.Run_run, DEVKIT_IMMEDIATE_ACTIVITY_TEMPLATE)
      .setDefaultText("MyStartupActivity")
  }

  override fun getActionName(directory: PsiDirectory?, newName: @NonNls String, templateName: @NonNls String?): @NlsContexts.Command String? {
    return DevKitBundle.message("action.name.new.devkit.startup.activity")
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
      DEVKIT_BACKGROUND_STARTUP_ACTIVITY_TEMPLATE -> "backgroundPostStartupActivity"
      DEVKIT_IMMEDIATE_ACTIVITY_TEMPLATE -> "postStartupActivity"
      else -> throw IllegalArgumentException("Unknown template name: $templateName")
    }

    @Suppress("DialogTitleCapitalization")
    runWriteCommandAction(file.project, DevKitBundle.message("command.register.startup.activity"), null, {
      val topLevelClasses = file.toUElementOfType<UFile>()?.classes
                              ?.takeIf { it.isNotEmpty() }
                            ?: return@runWriteCommandAction

      val rootTag = pluginXml.getDocument()?.rootTag ?: return@runWriteCommandAction

      if (IdeaPlugin.TAG_NAME == rootTag.name) {
        val extensionsTag = rootTag.findFirstSubTag("extensions") ?: return@runWriteCommandAction

        val newTag = extensionsTag.addSubTag(extensionsTag.createChildTag(tagName, extensionsTag.namespace, null, false), false)
        newTag.setAttribute("implementation", topLevelClasses.first().qualifiedName)
      }
    }, pluginXml)
  }
}