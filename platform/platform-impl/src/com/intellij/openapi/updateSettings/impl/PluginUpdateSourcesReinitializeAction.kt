// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class PluginUpdateSourcesReinitializeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = PluginUpdateSourceService.isFunctionalitySupported()
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!PluginUpdateSourceService.isFunctionalitySupported()) return
    (PluginUpdateSourceService.getInstance() as PluginUpdateSourceServiceImpl).resetPluginUpdateSources()
    val result = PluginUpdateSourceInitializer.enforceInitialization()
    val message: String
    val notificationType : NotificationType
    if (result) {
        message = IdeBundle.message("notification.content.plugin.update.sources.are.reset.and.initialized")
        notificationType = NotificationType.INFORMATION
    }
    else {
        message = IdeBundle.message("notification.content.plugin.update.sources.are.reset.but.not.initialized")
        notificationType = NotificationType.WARNING
    }
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Plugin Update Sources Reset")
      .createNotification(message, notificationType)
      .notify(e.project)
  }

}
