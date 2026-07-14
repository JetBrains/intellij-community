// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ide.plugins.PluginPermissionService
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder

/**
 * Removes all "Allow Always" decisions that [PluginPermissionServiceImpl] stores.
 *
 * The action asks the user to confirm the reset, and then shows a "System Messages" notification with the result.
 * When no decisions are stored, the action shows only a notification.
 * After the reset, each plugin permission request shows the permission dialog again.
 * The action has no group, so the user finds it with Find Action.
 */
internal class ResetPluginPermissionsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val service = PluginPermissionService.getInstance() as? PluginPermissionServiceImpl ?: return
    val project = e.project
    val title = IdeBundle.message("plugin.permission.reset.title")

    val count = service.storedDecisionCount
    if (count == 0) {
      NotificationGroupManager.getInstance().getNotificationGroup("System Messages")
        .createNotification(IdeBundle.message("plugin.permission.reset.nothing.message"), NotificationType.INFORMATION)
        .setDisplayId("plugin.permissions.reset.nothing")
        .notify(project)
      return
    }

    val confirmed = MessageDialogBuilder.yesNo(title, IdeBundle.message("plugin.permission.reset.confirmation.message", count))
      .yesText(IdeBundle.message("plugin.permission.reset.button"))
      .noText(CommonBundle.getCancelButtonText())
      .asWarning()
      .ask(project)
    if (!confirmed) {
      return
    }

    val removed = service.resetDecisions()
    NotificationGroupManager.getInstance().getNotificationGroup("System Messages")
      .createNotification(IdeBundle.message("plugin.permission.reset.done.title"),
                          IdeBundle.message("plugin.permission.reset.done.message", removed),
                          NotificationType.INFORMATION)
      .setDisplayId("plugin.permissions.reset")
      .notify(project)
  }
}
