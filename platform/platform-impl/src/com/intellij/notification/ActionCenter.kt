// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.ide.IdeBundle
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

object ActionCenter {
  @JvmStatic
  fun getNotifications(project: Project?): List<Notification> {
    return ApplicationNotificationsModel.getNotifications(project)
  }

  @Internal
  @JvmStatic
  fun expireNotifications(project: Project) {
    for (notification in getNotifications(project)) {
      notification.expire()
    }
  }

  @JvmStatic
  val toolwindowName: @Nls String
    get() = IdeBundle.message("toolwindow.stripe.Notifications")

  @RequiresEdt
  @JvmStatic
  fun showLog(project: Project) {
    project.service<NotificationsLogController>().show()
  }

  @RequiresEdt
  @JvmStatic
  @JvmOverloads
  fun activateLog(project: Project, focus: Boolean = true) {
    project.service<NotificationsLogController>().activate(focus)
  }

  @RequiresEdt
  @JvmStatic
  fun toggleLog(project: Project) {
    project.service<NotificationsLogController>().toggle()
  }
}