// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class PluginUpdateSourcesResetAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = PluginUpdateSourceService.isFunctionalitySupported()
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!PluginUpdateSourceService.isFunctionalitySupported()) return
    (PluginUpdateSourceService.getInstance() as PluginUpdateSourceServiceImpl).resetPluginUpdateSources()
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Plugin Update Sources Reset")
      .createNotification(IdeBundle.message("notification.content.plugin.update.sources.are.reset"), NotificationType.INFORMATION)
      .notify(e.project)
  }

}
