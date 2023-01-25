// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.messages

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.editorconfig.Utils
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.util.function.Function
import javax.swing.JComponent

class EditorConfigWrongFileNameNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) return null
    if (file.extension != EditorConfigFileConstants.FILE_EXTENSION) return null
    if (nameMatches(file)) return null
    val renameRunnable = createRenameRunnable(project, file)
    return Function { createNotificationPanel(file, it, project, renameRunnable) }
  }
  private fun createNotificationPanel(file: VirtualFile,
                                      fileEditor: FileEditor,
                                      project: Project,
                                      renameRunnable: Runnable?): EditorNotificationPanel? {
    if (fileEditor !is TextEditor) return null
    val editor = fileEditor.editor
    if (editor.getUserData(HIDDEN_KEY) != null) return null
    return buildPanel(editor, file, project, renameRunnable)
  }

  private fun createRenameRunnable(project: Project, file: VirtualFile): Runnable? {
    if (findEditorConfig(file) == null) {
      return Runnable {
        if (runReadAction { findEditorConfig(file) } != null) {
          file.parent.findChild(EditorConfigFileConstants.FILE_NAME)
          val message = EditorConfigBundle["notification.error.file.already.exists"]
          error(message, project)
          return@Runnable
        }

        try {
          runWriteAction { file.rename(this, EditorConfigFileConstants.FILE_NAME) }
        }
        catch (ex: IOException) {
          val message = EditorConfigBundle.get("notification.error.ioexception", ex.message ?: "")
          error(message)
        }

        update(file, project)
      }
    }
    return null
  }

  private fun buildPanel(editor: Editor, file: VirtualFile, project: Project, renameRunnable: Runnable?): EditorNotificationPanel {
    val result = EditorNotificationPanel(editor, null, null, EditorNotificationPanel.Status.Warning)

    if (renameRunnable != null) {
      val rename = EditorConfigBundle["notification.action.rename"]
      result.createActionLabel(rename, renameRunnable)
    }

    val hide = EditorConfigBundle["notification.action.hide.once"]
    result.createActionLabel(hide) {
      editor.putUserData<Boolean>(HIDDEN_KEY, true)
      update(file, project)
    }

    val hideForever = EditorConfigBundle["notification.action.hide.forever"]
    result.createActionLabel(hideForever) {
      PropertiesComponent.getInstance().setValue(DISABLE_KEY, true)
      update(file, project)
    }

    return result.text(EditorConfigBundle.get("notification.rename.message"))
  }

  private fun findEditorConfig(file: VirtualFile) = file.parent.findChild(EditorConfigFileConstants.FILE_NAME)

  private fun error(@Nls message: String, project: Project) {
    val notification = Notification("editorconfig", Utils.EDITOR_CONFIG_NAME, message, NotificationType.ERROR)
    Notifications.Bus.notify(notification, project)
  }

  private fun nameMatches(file: VirtualFile) = file.nameWithoutExtension == EditorConfigFileConstants.FILE_NAME_WITHOUT_EXTENSION
  private fun update(file: VirtualFile, project: Project) = EditorNotifications.getInstance(project).updateNotifications(file)

  private companion object {
    private val HIDDEN_KEY = Key.create<Boolean>("editorconfig.wrong.name.notification.hidden")
    private const val DISABLE_KEY = "editorconfig.wrong.name.notification.disabled"
  }
}
