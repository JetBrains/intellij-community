// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.usageNotification

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

private const val UI_DSL_PACKAGE = "com.intellij.ui.dsl"
private val NOTIFICATION_ALLOWED_KEY = Key.create<Boolean>("KotlinUiDslUsageNotificationProvider.notificationAllowed")
private const val NOTIFICATION_ENABLED_KEY = "devkit.uiDsl.usage.notification.enabled"
private const val NOTIFICATION_ENABLED_DEFAULT = true

private val isNotificationFeatureEnabled: Boolean
  get() = Registry.`is`("devkit.uiDsl.usage.notification.feature.enabled", true)

private var isNotificationEnabled: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(NOTIFICATION_ENABLED_KEY, NOTIFICATION_ENABLED_DEFAULT)
  set(value) {
    PropertiesComponent.getInstance().setValue(NOTIFICATION_ENABLED_KEY, value, NOTIFICATION_ENABLED_DEFAULT)
  }

internal class KotlinUiDslUsageNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    if (!isNotificationFeatureEnabled || !isNotificationEnabled || file.extension != "kt") {
      return null
    }

    val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
    val notificationAllowed = file.getOrCreateUserData(NOTIFICATION_ALLOWED_KEY) {
      !isUiDslUsed(ktFile)
    }

    if (!(notificationAllowed && isUiDslUsed(ktFile))) {
      return null
    }

    return Function { editor ->
      UiDslEditorNotificationPanel(project, file, editor)
    }
  }

  private fun isUiDslUsed(file: KtFile): Boolean {
    return file.importDirectives.any {
      it.importPath?.pathStr?.startsWith(UI_DSL_PACKAGE) == true
    }
  }
}

private class UiDslEditorNotificationPanel(project: Project, file: VirtualFile, fileEditor: FileEditor) :
  EditorNotificationPanel(fileEditor, Status.Info) {

  init {
    text = DevkitUiDslBundle.message("kotlin.ui.dsl.usage.notification")

    myTextLabel.editorPane?.let { editorPane ->
      // Remove listeners that open the browser
      for (listener in editorPane.hyperlinkListeners) {
        if (listener is BrowserHyperlinkListener) {
          editorPane.removeHyperlinkListener(listener)
        }
      }

      editorPane.addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          executeAction("UiDslShowcaseAction", it)
        }
      }
    }

    createActionLabel(DevkitUiDslBundle.message("kotlin.ui.dsl.usage.notification.do.not.show.again")) {
      isNotificationEnabled = false
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    setCloseAction {
      file.putUserData(NOTIFICATION_ALLOWED_KEY, false)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }
}
