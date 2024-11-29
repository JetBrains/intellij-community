// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * Service to synchronize notifications showing during hot swap.
 * @see trackNotification
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapStatusNotificationManager private constructor(private val project: Project) : Disposable.Default {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapStatusNotificationManager = project.service()
  }

  private val notifications = CopyOnWriteArrayList<Notification>()

  /**
   * Add the notification to a tracking list, that will be cleaned after [clearNotifications] call.
   */
  fun trackNotification(notification: Notification) {
    notification.whenExpired { notifications.remove(notification) }
    notifications.add(notification)
  }

  /**
   * Expire all previously added notifications.
   */
  internal fun clearNotifications() {
    notifications.toArray().forEach { (it as Notification).expire() }
  }

  internal fun showSuccessNotification(scope: CoroutineScope) {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("HotSwap Messages")
      .createNotification(XDebuggerBundle.message("xdebugger.hotswap.status.success"), NotificationType.INFORMATION)
    trackNotification(notification)
    notification.icon = AllIcons.Status.Success
    notification.notify(project)
    scope.launch(Dispatchers.Default) {
      delay(NOTIFICATION_TIME_SECONDS.seconds)
      notification.expire()
    }
  }
}

@ApiStatus.Internal
const val NOTIFICATION_TIME_SECONDS: Int = 3