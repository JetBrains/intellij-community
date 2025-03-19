// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import org.jetbrains.annotations.ApiStatus

// not REALLY a listener, but should be made into one eventually
@ApiStatus.Internal
interface ProjectNotificationsModelListener {
  fun add(notification: Notification)
  fun add(notifications: List<Notification>)

  fun getNotifications(): List<Notification>
  fun isEmpty(): Boolean

  fun clearUnreadStates()

  fun remove(notification: Notification)
  fun expireAll()
  fun clearTimeline()
  fun clearAll()
}