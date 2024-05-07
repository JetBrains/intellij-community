// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project

internal class StickyLinesSettingsDisableAction : StickyLinesAbstractAction() {

  override fun update(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    e.presentation.isEnabledAndVisible = settings.areStickyLinesShown()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    if (settings.areStickyLinesShown()) {
      settings.setStickyLinesShown(false)
      stickyLinesDisabledNotification(e.project).notify(e.project)
    }
  }

  private fun stickyLinesDisabledNotification(project: Project?): Notification {
    return Notification(
      "Sticky Lines",
      ApplicationBundle.message("settings.editor.sticky.lines.disabled.title"),
      ApplicationBundle.message("settings.editor.sticky.lines.disabled.text"),
      NotificationType.INFORMATION
    ).addAction(NotificationAction.createSimpleExpiring(
      ApplicationBundle.message("settings.editor.general.appearance"),
      Runnable {
        showStickyLinesSettingsDialog(project)
      }
    ))
  }
}
