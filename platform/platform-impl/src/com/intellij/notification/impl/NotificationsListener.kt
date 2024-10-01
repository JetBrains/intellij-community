// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationRouter
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationsManagerImpl.isDummyEnvironment
import com.intellij.openapi.diagnostic.logger
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
    for (listener in NotificationRouter.EP_NAME.extensionList) {
      try {
        if (listener.routeNotification(notification, project)) {
          return
        }
      } catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  companion object {
    private val LOG = logger<NotificationsListener>()
  }
}
