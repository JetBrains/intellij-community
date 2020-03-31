// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle

import java.util.concurrent.atomic.AtomicReference

class SingletonNotificationManager(private val group: NotificationGroup, private val type: NotificationType, private val defaultListener: NotificationListener? = null) {
  private val notification = AtomicReference<Notification>()

  private val expiredListener = Runnable {
    val currentNotification = notification.get()
    if (currentNotification != null && currentNotification.isExpired) {
      notification.compareAndSet(currentNotification, null)
    }
  }

  fun notify(content: @NotificationContent String, project: Project?): Boolean {
    return notify("", content, project)
  }

  @JvmOverloads
  fun notify(title: @NotificationTitle String = "",
             content: @NotificationContent String,
             project: Project? = null, listener: NotificationListener? = defaultListener, action: AnAction? = null): Boolean {
    val oldNotification = notification.get()
    // !oldNotification.isExpired() is not enough - notification could be closed, but not expired
    if (oldNotification != null) {
      val toolWindowId = group.toolWindowId
      if (!oldNotification.isExpired && toolWindowId != null && (oldNotification.balloon != null || project != null &&
                                                                 group.displayType == NotificationDisplayType.TOOL_WINDOW &&
                                                                 ToolWindowManager.getInstance(project).getToolWindowBalloon(toolWindowId) != null)) {
        return false
      }
      oldNotification.whenExpired(null)
      oldNotification.expire()
    }

    val newNotification = group.createNotification(title, content, type, listener)
    if (action != null) {
      newNotification.addAction(action)
    }
    newNotification.whenExpired(expiredListener)
    notification.set(newNotification)
    newNotification.notify(project)
    return true
  }

  fun clear() {
    notification.getAndSet(null)?.let {
      it.whenExpired(null)
      it.expire()
    }
  }
}