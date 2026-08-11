// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationRouter
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationsManagerImpl.isDummyEnvironment
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NotificationsListener : Notifications {
  private val project: Project?

  @Suppress("unused")
  constructor() {
    project = null
  }

  @Suppress("unused")
  private constructor(project: Project?) {
    this.project = project
    if (isDummyEnvironment()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun notify(notification: Notification) {
    NotificationRouter.EP_NAME.findFirstSafe {
      it.routeNotification(notification, project)
    }
  }
}
