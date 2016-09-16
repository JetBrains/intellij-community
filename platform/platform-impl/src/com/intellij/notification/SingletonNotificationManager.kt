/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

import java.util.concurrent.atomic.AtomicReference

class SingletonNotificationManager(private val group: NotificationGroup, private val type: NotificationType, private val listener: NotificationListener?) {
  private val notification = AtomicReference<Notification>()

  private val expiredListener by lazy {
    Runnable {
      val currentNotification = notification.get()
      if (currentNotification != null && currentNotification.isExpired) {
        notification.compareAndSet(currentNotification, null)
      }
    }
  }

  fun notify(content: String, project: Project?): Boolean {
    return notify("", content, project, listener)
  }

  @JvmOverloads
  fun notify(title: String, content: String, project: Project? = null, listener: NotificationListener? = null): Boolean {
    val oldNotification = notification.get()
    // !oldNotification.isExpired() is not enough - notification could be closed, but not expired
    if (oldNotification != null) {
      if (!oldNotification.isExpired && (oldNotification.balloon != null || project != null &&
          group.displayType == NotificationDisplayType.TOOL_WINDOW &&
          ToolWindowManager.getInstance(project).getToolWindowBalloon(group.toolWindowId) != null)) {
        return false
      }
      oldNotification.whenExpired(null)
      oldNotification.expire()
    }

    val newNotification = group.createNotification(title, content, type, listener)
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