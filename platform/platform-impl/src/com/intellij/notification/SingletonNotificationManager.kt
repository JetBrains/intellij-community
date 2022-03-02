// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class SingletonNotificationManager(groupId: String, private val type: NotificationType) {
  private val group = NotificationGroupManager.getInstance().getNotificationGroup(groupId)
  private val notification = AtomicReference<Notification>()

  fun notify(@NotificationTitle title: String, @NotificationContent content: String, project: Project) =
    notify(title, content, project) { }

  fun notify(@NotificationTitle title: String,
             @NotificationContent content: String,
             project: Project?,
             customizer: Consumer<Notification>) {
    val oldNotification = notification.get()
    if (oldNotification != null) {
      if (isVisible(oldNotification, project)) {
        return
      }
      oldNotification.expire()
    }

    val newNotification = object : Notification(group.displayId, title, content, type) {
      override fun expire() {
        super.expire()
        notification.compareAndSet(this, null)
      }
    }
    customizer.accept(newNotification)

    if (notification.compareAndSet(oldNotification, newNotification)) {
      newNotification.notify(project)
    }
    else {
      newNotification.expire()
    }
  }

  private fun isVisible(notification: Notification, project: Project?): Boolean {
    val balloon = when {
      group.displayType != NotificationDisplayType.TOOL_WINDOW -> notification.balloon
      project != null -> ToolWindowManager.getInstance(project).getToolWindowBalloon(group.toolWindowId!!)
      else -> null
    }
    return balloon != null && !balloon.isDisposed
  }

  fun clear() {
    notification.getAndSet(null)?.expire()
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("please use `#notify(String, String, Project)` instead")
  @ApiStatus.ScheduledForRemoval
  fun notify(@NotificationContent content: String, project: Project?): Boolean {
    notify("", content, project) { }
    return true
  }
  //</editor-fold>
}
