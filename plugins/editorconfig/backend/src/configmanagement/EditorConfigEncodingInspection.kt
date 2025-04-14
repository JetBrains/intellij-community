// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.*
import com.intellij.editorconfig.common.EditorConfigBundle.message
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.editorconfig.EditorConfigNotifier
import org.editorconfig.Utils.isEnabled
import org.jetbrains.annotations.Nls
import java.io.IOException

internal class EditorConfigEncodingInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile,
                         manager: InspectionManager,
                         isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file != getMainPsi(file)) {
      return null
    }
    val project = manager.project
    val virtualFile = file.virtualFile
    if (virtualFile == null || !isEnabled(CodeStyle.getSettings(project)) || !virtualFile.isWritable) {
      return null
    }

    if (isHardcodedCharsetOrFailed(virtualFile)) {
      return null
    }

    val encodingCache: EditorConfigEncodingCache = EditorConfigEncodingCache.getInstance()
    if (encodingCache.isIgnored(virtualFile)) {
      return null
    }
    val charsetData = encodingCache.getCharsetData(file.project, file.virtualFile, false) ?: return null

    val charset = charsetData.getCharset()
    if (charset != null && virtualFile.charset != charset) {
      return arrayOf(
        manager.createProblemDescriptor(
          file,
          message("inspection.file.encoding.mismatch.descriptor", charset.displayName()),
          arrayOf(
            ApplyEditorConfigEncodingQuickFix(),
            IgnoreFileQuickFix()
          ),
          ProblemHighlightType.WARNING,
          isOnTheFly,
          false)
      )
    }
    return null
  }

  private class ApplyEditorConfigEncodingQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = message("inspection.file.encoding.apply")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val file = descriptor.psiElement.containingFile.virtualFile
      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document != null) {
        EditorConfigEncodingCache.getInstance().computeAndCacheEncoding(project, file)
        try {
          LoadTextUtil.write(project, file, file, document.text, document.modificationStamp)
        }
        catch (e: IOException) {
          showError(project, message("inspection.file.encoding.save.error"), e.localizedMessage)
        }
      }
    }

    private fun showError(project: Project, title: @Nls String, message: @Nls String) {
      val group = NotificationGroupManager.getInstance().getNotificationGroup(EditorConfigNotifier.GROUP_DISPLAY_ID)
      Notifications.Bus.notify(group.createNotification(title, message, NotificationType.ERROR), project)
    }
  }

  private class IgnoreFileQuickFix : LocalQuickFix {
    override fun getFamilyName(): String {
      return message("inspection.file.encoding.ignore")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val file = descriptor.psiElement.containingFile.virtualFile
      EditorConfigEncodingCache.getInstance().setIgnored(file)
    }
  }

  private fun isHardcodedCharsetOrFailed(virtualFile: VirtualFile): Boolean {
    val fileType = virtualFile.fileType
    return try {
      val charsetName = fileType.getCharset(virtualFile, virtualFile.contentsToByteArray())
      charsetName != null
    }
    catch (e: IOException) {
      true
    }
  }

  private fun getMainPsi(psiFile: PsiFile): PsiFile {
    val baseLanguage = psiFile.viewProvider.baseLanguage
    return psiFile.viewProvider.getPsi(baseLanguage)
  }

}