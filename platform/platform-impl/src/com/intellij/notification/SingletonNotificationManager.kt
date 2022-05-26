// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.openapi.actionSystem.AnAction
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
  private var defaultListener: NotificationListener? = null

  private val expiredListener = Runnable {
    val currentNotification = notification.get()
    if (currentNotification != null && currentNotification.isExpired) {
      notification.compareAndSet(currentNotification, null)
    }
  }

  fun notify(@NotificationTitle title: String, @NotificationContent content: String, project: Project) {
    notify(title, content, project) { }
  }

  fun notify(@NotificationTitle title: String,
             @NotificationContent content: String,
             project: Project?,
             customizer: Consumer<Notification>) {
    val oldNotification = notification.get()
    if (oldNotification != null) {
      if (isVisible(oldNotification, project)) {
        return
      }
      expire(oldNotification)
    }

    val newNotification = Notification(group.displayId, title, content, type)
    customizer.accept(newNotification)
    newNotification.whenExpired(expiredListener)

    if (notification.compareAndSet(oldNotification, newNotification)) {
      newNotification.notify(project)
    }
    else {
      expire(newNotification)
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
    notification.getAndSet(null)?.let { expire(it) }
  }

  private fun expire(notification: Notification) {
    notification.resetAllExpiredListeners()
    notification.expire()
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("please use `#SingletonNotificationManager(String, NotificationType)` instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  constructor(group: NotificationGroup, type: NotificationType, defaultListener: NotificationListener? = null) : this(group.displayId, type) {
    this.defaultListener = defaultListener
  }

  @Deprecated("please use `#notify(String, String, Project)` instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  fun notify(@NotificationContent content: String, project: Project?): Boolean {
    notify("", content, project) { }
    return true
  }

  @JvmOverloads
  @Deprecated("please use `#notify(String, String, Project, Consumer<Notification>)` instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  fun notify(@NotificationTitle title: String = "",
             @NotificationContent content: String,
             project: Project? = null,
             listener: NotificationListener? = defaultListener,
             action: AnAction? = null): Boolean {
    notify(title, content, project) { notification ->
      action?.let { notification.addAction(it) }
      listener?.let { notification.setListener(it) }
    }
    return true
  }
  //</editor-fold>
}
