// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationRouter
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LocalNotificationRouter: NotificationRouter {
  override fun routeNotification(notification: Notification, project: Project?): Boolean {
    NotificationsManagerImpl.getNotificationsManager().showNotification(notification, project)
    return true
  }
}
