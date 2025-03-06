// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.ActionCenter
import com.intellij.notification.Notification
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ApplicationNotificationsModel {
  private val notifications = ArrayList<Notification>()
  private val projectToModel = HashMap<Project, ProjectNotificationModel>()
  private val dataGuard = Object()

  @JvmStatic
  fun addNotification(project: Project?, notification: Notification) {
    val callback = synchronized(dataGuard) {
      if (project == null) {
        addApplicationNotification(notification)
      }
      else {
        addProjectNotification(project, notification)
      }
    } ?: return

    UIUtil.invokeLaterIfNeeded {
      callback.run()
    }
  }

  private fun addApplicationNotification(notification: Notification): Runnable? {
    if (projectToModel.isEmpty()) {
      notifications.add(notification)
      return null
    }
    else {
      val callbacks = mutableListOf<Runnable>()
      for ((project, model) in projectToModel.entries) {
        val callback = model.addNotification(project, notification, notifications)
        callbacks.add(callback)
      }
      return Runnable {
        for (callback in callbacks) {
          callback.run()
        }
      }
    }
  }

  private fun addProjectNotification(project: Project, notification: Notification): Runnable {
    val model = projectToModel.getOrPut(project) { newProjectModel(project) }
    return model.addNotification(project, notification, notifications)
  }

  @JvmStatic
  fun getNotifications(project: Project?): List<Notification> {
    synchronized(dataGuard) {
      if (project == null) {
        return getApplicationNotifications()
      }
      else {
        return getProjectNotifications(project)
      }
    }
  }

  private fun getApplicationNotifications(): List<Notification> {
    val allNotifications = notifications.toMutableList()
    projectToModel.values.flatMapTo(allNotifications) { model ->
      model.getNotifications(emptyList())
    }
    return allNotifications
  }

  private fun getProjectNotifications(project: Project): List<Notification> {
    return projectToModel[project]?.getNotifications(notifications)
           ?: notifications.toList()
  }

  @JvmStatic
  fun getStateNotifications(project: Project): List<Notification> {
    return synchronized(dataGuard) {
      projectToModel[project]?.getStateNotifications().orEmpty()
    }
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    synchronized(dataGuard) {
      projectToModel[project]?.setStatusMessage(project, notification)
    }
  }

  @JvmStatic
  fun getStatusMessage(project: Project): StatusMessage? {
    synchronized(dataGuard) {
      return projectToModel[project]?.getStatusMessage()
    }
  }

  fun isEmptyContent(project: Project): Boolean {
    return projectToModel[project]?.isEmptyContent() ?: true
  }

  @JvmStatic
  fun expire(notification: Notification) {
    val callback = synchronized(dataGuard) {
      notifications.remove(notification)

      val callbacks = mutableListOf<Runnable>()
      for ((project, model) in projectToModel) {
        val callback = model.expire(project, notification)
        callbacks.add(callback)
      }

      Runnable {
        for (callback in callbacks) {
          callback.run()
        }
      }
    }

    UIUtil.invokeLaterIfNeeded {
      callback.run()
    }
  }

  fun clearAll(project: Project?) {
    val callback = synchronized(dataGuard) {
      notifications.clear()
      if (project != null) {
        projectToModel[project]?.clearAll(project)
      }
      else {
        null
      }
    } ?: return

    UIUtil.invokeLaterIfNeeded {
      callback.run()
    }
  }

  fun expireAll() {
    val callback = synchronized(dataGuard) {
      val allNotifications = notifications.toMutableList()
      notifications.clear()

      val callbacks = mutableListOf<Runnable>()
      for ((project, model) in projectToModel) {
        val (projectNotifications, callback) = model.expireAll(project)
        allNotifications.addAll(projectNotifications)
        callbacks.add(callback)
      }

      Runnable {
        UIUtil.invokeLaterIfNeeded {
          for (callback in callbacks) {
            callback.run()
          }
        }

        for (notification in allNotifications) {
          notification.expire()
        }
      }
    }
    callback.run()
  }

  internal fun registerAndGetInitNotifications(content: NotificationContent): List<Notification> {
    synchronized(dataGuard) {
      val initNotifications = notifications.toMutableList()
      notifications.clear()

      val model = projectToModel.getOrPut(content.project) { newProjectModel(content.project) }
      model.registerAndGetInitNotifications(content).also {
        initNotifications.addAll(it)
      }
      return initNotifications.toList()
    }
  }

  internal fun unregister(content: NotificationContent) {
    synchronized(dataGuard) {
      projectToModel.remove(content.project)
    }
  }

  private fun newProjectModel(project: Project): ProjectNotificationModel {
    Disposer.register(project) {
      synchronized(dataGuard) {
        projectToModel.remove(project)
      }
    }
    return ProjectNotificationModel()
  }
}

private class ProjectNotificationModel {
  private val myNotifications = ArrayList<Notification>()
  private var myContent: NotificationContent? = null
  private var myStatusMessage: StatusMessage? = null

  fun registerAndGetInitNotifications(content: NotificationContent): List<Notification> {
    val initNotifications = myNotifications.toList()
    myNotifications.clear()
    myContent = content
    return initNotifications
  }

  fun addNotification(project: Project, notification: Notification, appNotifications: List<Notification>): Runnable {
    if (myContent == null) {
      myNotifications.add(notification)

      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)

      return Runnable { updateToolWindow(project, notification, notifications, false) }
    }
    else {
      return Runnable { myContent!!.add(notification) }
    }
  }

  fun getStateNotifications(): List<Notification> {
    if (myContent == null) {
      return emptyList()
    }
    return myContent!!.getStateNotifications()
  }

  fun isEmptyContent(): Boolean {
    return myContent == null || myContent!!.isEmpty()
  }

  fun getNotifications(appNotifications: List<Notification>): List<Notification> {
    if (myContent == null) {
      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)
      return notifications
    }
    return myContent!!.getNotifications()
  }

  fun expire(project: Project, notification: Notification): Runnable {
    myNotifications.remove(notification)
    return if (myContent == null) {
      Runnable { updateToolWindow(project, null, myNotifications, false) }
    }
    else {
      Runnable { myContent!!.expire(notification) }
    }
  }

  fun expireAll(project: Project): Pair<List<Notification>, Runnable> {
    val notifications = myNotifications.toList()
    myNotifications.clear()
    if (myContent == null) {
      return notifications to Runnable { updateToolWindow(project, null, emptyList(), false) }
    }
    else {
      return notifications to Runnable { myContent!!.expire(null) }
    }
  }

  fun clearAll(project: Project): Runnable {
    myNotifications.clear()
    if (myContent == null) {
      return Runnable { updateToolWindow(project, null, emptyList(), true) }
    }
    else {
      return Runnable { myContent!!.clearAll() }
    }
  }

  private fun updateToolWindow(
    project: Project,
    stateNotification: Notification?,
    notifications: List<Notification>,
    closeBalloons: Boolean,
  ) {
    if (project.isDisposed) {
      return
    }

    setStatusMessage(project, stateNotification)

    if (closeBalloons) {
      project.closeAllBalloons()
    }

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
    toolWindow?.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(notifications))
  }

  fun getStatusMessage(): StatusMessage? {
    return myStatusMessage
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    if ((myStatusMessage == null && notification == null) || (myStatusMessage != null && myStatusMessage!!.notification === notification)) {
      return
    }
    myStatusMessage = if (notification == null) {
      null
    }
    else {
      StatusMessage(notification, NotificationsUtil.buildStatusMessage(notification), notification.timestamp)
    }
    StatusBar.Info.set("", project, ActionCenter.EVENT_REQUESTOR)
  }
}