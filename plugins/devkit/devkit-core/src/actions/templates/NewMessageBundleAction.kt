// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.actions.CustomCreateFromTemplateAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.obsolete.getOrCreateBundleResourcesRoot
import org.jetbrains.idea.devkit.util.PsiUtil

internal class NewMessageBundleAction : CustomCreateFromTemplateAction("DevKit MessageBundle") {
  override fun customizeBuilder(builder: CreateFileFromTemplateDialog.Builder) {
    builder.setDefaultText("MyMessageBundle")
    builder.setTitle(DevKitBundle.message("action.create.message.bundle.title"))
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    return super.isAvailable(dataContext) && isDevKitClassTemplateAvailable(dataContext)
  }

  override fun postProcess(createdElement: PsiFile, dataContext: DataContext,
                           templateName: String?, customProperties: Map<String, String>?) {
    super.postProcess(createdElement, dataContext, templateName, customProperties)

    runWriteCommandAction(createdElement.project, DevKitBundle.message("command.create.bundle.properties"), null, {
      val module = dataContext.getData(PlatformCoreDataKeys.MODULE)
      if (module == null || !PsiUtil.isPluginModule(module)) {
        return@runWriteCommandAction
      }

      val resourcesRoot = getOrCreateBundleResourcesRoot(module) ?: return@runWriteCommandAction

      val messagesDirName = "messages"
      val messagesDir = resourcesRoot.findSubdirectory(messagesDirName) ?: resourcesRoot.createSubdirectory(messagesDirName)
      messagesDir.createFile("${FileUtil.getNameWithoutExtension(createdElement.name)}.properties")
    })
  }
}