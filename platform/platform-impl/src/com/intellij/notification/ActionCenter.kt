// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.ide.IdeBundle
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.notification.impl.NotificationsToolWindowFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

object ActionCenter {
  @Internal
  const val EVENT_REQUESTOR: String = "Internal notification event requestor"

  @JvmField
  @Internal
  val MODEL_CHANGED: Topic<EventListener> = Topic.create("NOTIFICATION_MODEL_CHANGED",
                                                         EventListener::class.java, Topic.BroadcastDirection.NONE)

  @Internal
  fun fireModelChanged() {
    ApplicationManager.getApplication().messageBus.syncPublisher(MODEL_CHANGED).modelChanged()
  }

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

  @JvmStatic
  fun showLog(project: Project) {
    getToolWindow(project)?.show()
  }

  @JvmStatic
  @JvmOverloads
  fun activateLog(project: Project, focus: Boolean = true) {
    getToolWindow(project)?.activate(null, focus)
  }

  @JvmStatic
  fun toggleLog(project: Project) {
    val toolWindow = getToolWindow(project) ?: return
    if (toolWindow.isVisible) {
      toolWindow.hide()
    }
    else {
      toolWindow.activate(null)
    }
  }

  private fun getToolWindow(project: Project): ToolWindow? {
    return ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
  }

  interface EventListener {
    fun modelChanged()
  }
}