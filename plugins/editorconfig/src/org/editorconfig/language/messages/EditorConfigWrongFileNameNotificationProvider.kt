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
import com.intellij.ui.EditorNotifications
import org.editorconfig.Utils
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.jetbrains.annotations.Nls
import java.io.IOException

class EditorConfigWrongFileNameNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {
  override fun getKey() = KEY
  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    fileEditor as? TextEditor ?: return null
    val editor = fileEditor.editor
    if (editor.getUserData(HIDDEN_KEY) != null) return null
    if (PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) return null
    if (file.extension != EditorConfigFileConstants.FILE_EXTENSION) return null
    if (nameMatches(file)) return null
    return buildPanel(editor, file, project)
  }

  private fun buildPanel(editor: Editor, file: VirtualFile, project: Project): EditorNotificationPanel {
    val result = EditorNotificationPanel(editor, null, null)

    if (findEditorConfig(file) == null) {
      val rename = EditorConfigBundle["notification.action.rename"]
      result.createActionLabel(rename) action@{
        if (findEditorConfig(file) != null) {
          val message = EditorConfigBundle["notification.error.file.already.exists"]
          error(message, project)
          return@action
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

  private fun findEditorConfig(file: VirtualFile) =
    runReadAction { file.parent.findChild(EditorConfigFileConstants.FILE_NAME) }

  private fun error(@Nls message: String, project: Project) {
    val notification = Notification("editorconfig", Utils.EDITOR_CONFIG_NAME, message, NotificationType.ERROR)
    Notifications.Bus.notify(notification, project)
  }

  private fun nameMatches(file: VirtualFile) = file.nameWithoutExtension == EditorConfigFileConstants.FILE_NAME_WITHOUT_EXTENSION
  private fun update(file: VirtualFile, project: Project) = EditorNotifications.getInstance(project).updateNotifications(file)

  private companion object {
    private val KEY = Key.create<EditorNotificationPanel>("editorconfig.wrong.name.notification")
    private val HIDDEN_KEY = Key.create<Boolean>("editorconfig.wrong.name.notification.hidden")
    private const val DISABLE_KEY = "editorconfig.wrong.name.notification.disabled"
  }
}
