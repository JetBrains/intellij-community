// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.atomic.AtomicReference

class SingletonNotificationManager(private val group: NotificationGroup, private val type: NotificationType, private val defaultListener: NotificationListener? = null) {
  private val notification = AtomicReference<Notification>()

  private val expiredListener = Runnable {
    val currentNotification = notification.get()
    if (currentNotification != null && currentNotification.isExpired) {
      notification.compareAndSet(currentNotification, null)
    }
  }

  fun notify(@NotificationContent content: String, project: Project?): Boolean {
    return notify("", content, project)
  }

  @JvmOverloads
  fun notify(@NotificationTitle title: String = "",
             @NotificationContent content: String,
             project: Project? = null,
             listener: NotificationListener? = defaultListener,
             action: AnAction? = null): Boolean {
    val oldNotification = notification.get()
    if (oldNotification != null) {
      if (isVisible(oldNotification, project)) {
        return false
      }
      oldNotification.whenExpired(null)
      oldNotification.expire()
    }

    val newNotification = group.createNotification(title, content, type)
    if (action != null) {
      newNotification.addAction(action)
    }
    if (listener != null) {
      newNotification.setListener(listener)
    }
    newNotification.whenExpired(expiredListener)
    notification.set(newNotification)
    newNotification.notify(project)
    return true
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
    notification.getAndSet(null)?.let {
      it.whenExpired(null)
      it.expire()
    }
  }
}
