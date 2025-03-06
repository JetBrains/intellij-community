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
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ApplicationNotificationsModel {
  private val lazyModel = lazy(::ApplicationNotificationModelDelegate)
  private val model: ApplicationNotificationModelDelegate by lazyModel

  @JvmStatic
  private fun getModelIfCreated(): ApplicationNotificationModelDelegate? = lazyModel.takeIf { it.isInitialized() }?.value

  @JvmStatic
  fun addNotification(project: Project?, notification: Notification) {
    model.addNotification(project, notification)
  }

  @JvmStatic
  fun getNotifications(project: Project?): List<Notification> {
    return getModelIfCreated()?.getNotifications(project).orEmpty()
  }

  @JvmStatic
  fun getStateNotifications(project: Project): List<Notification> {
    return getModelIfCreated()?.getStateNotifications(project).orEmpty()
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    model.setStatusMessage(project, notification)
  }

  @JvmStatic
  fun getStatusMessage(project: Project): StatusMessage? {
    return getModelIfCreated()?.getStatusMessage(project)
  }

  fun isEmptyContent(project: Project): Boolean {
    return getModelIfCreated()?.isEmptyContent(project) ?: true
  }

  @JvmStatic
  fun expire(notification: Notification) {
    getModelIfCreated()?.expire(notification)
  }

  fun clearAll(project: Project?) {
    getModelIfCreated()?.clearAll(project)
  }

  fun expireAll() {
    getModelIfCreated()?.expireAll()
  }

  internal fun registerAndGetInitNotifications(notificationContent: NotificationContent, newNotifications: ArrayList<Notification>) {
    model.registerAndGetInitNotifications(notificationContent, newNotifications)
  }

  internal fun unregister(notificationContent: NotificationContent) {
    model.unregister(notificationContent)
  }
}

private class ApplicationNotificationModelDelegate {
  private val myNotifications = ArrayList<Notification>()
  private val myProjectToModel = HashMap<Project, ProjectNotificationModel>()
  private val myLock = Object()

  fun registerAndGetInitNotifications(content: NotificationContent, notifications: MutableList<Notification>) {
    synchronized(myLock) {
      notifications.addAll(myNotifications)
      myNotifications.clear()

      val model = myProjectToModel.getOrPut(content.project) { newProjectModel(content.project) }
      model.registerAndGetInitNotifications(content, notifications)
    }
  }

  fun unregister(content: NotificationContent) {
    synchronized(myLock) {
      myProjectToModel.remove(content.project)
    }
  }

  fun addNotification(project: Project?, notification: Notification) {
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      if (project == null) {
        if (myProjectToModel.isEmpty()) {
          myNotifications.add(notification)
        }
        else {
          for ((eachProject, eachModel) in myProjectToModel.entries) {
            eachModel.addNotification(eachProject, notification, myNotifications, runnables)
          }
        }
      }
      else {
        val model = myProjectToModel.getOrPut(project) { newProjectModel(project) }
        model.addNotification(project, notification, myNotifications, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }
  }

  private fun newProjectModel(project: Project): ProjectNotificationModel {
    Disposer.register(project) {
      synchronized(myLock) {
        myProjectToModel.remove(project)
      }
    }
    return ProjectNotificationModel()
  }

  fun getStateNotifications(project: Project): List<Notification> {
    synchronized(myLock) {
      val model = myProjectToModel[project]
      if (model != null) {
        return model.getStateNotifications()
      }
    }
    return emptyList()
  }

  fun getNotifications(project: Project?): List<Notification> {
    synchronized(myLock) {
      if (project == null) {
        val result = ArrayList(myNotifications)
        for ((_, eachModel) in myProjectToModel.entries) {
          result.addAll(eachModel.getNotifications(emptyList()))
        }
        return result
      }
      val model = myProjectToModel[project]
      if (model == null) {
        return ArrayList(myNotifications)
      }
      return model.getNotifications(myNotifications)
    }
  }

  fun isEmptyContent(project: Project): Boolean {
    val model = myProjectToModel[project]
    return model == null || model.isEmptyContent()
  }

  fun expire(notification: Notification) {
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      myNotifications.remove(notification)
      for ((project, model) in myProjectToModel) {
        model.expire(project, notification, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }
  }

  fun expireAll() {
    val notifications = ArrayList<Notification>()
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      notifications.addAll(myNotifications)
      myNotifications.clear()
      for ((project, model) in myProjectToModel) {
        model.expireAll(project, notifications, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }

    for (notification in notifications) {
      notification.expire()
    }
  }

  fun clearAll(project: Project?) {
    synchronized(myLock) {
      myNotifications.clear()
      if (project != null) {
        myProjectToModel[project]?.clearAll(project)
      }
    }
  }

  fun getStatusMessage(project: Project): StatusMessage? {
    synchronized(myLock) {
      return myProjectToModel[project]?.getStatusMessage()
    }
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    synchronized(myLock) {
      myProjectToModel[project]?.setStatusMessage(project, notification)
    }
  }
}

private class ProjectNotificationModel {
  private val myNotifications = ArrayList<Notification>()
  private var myContent: NotificationContent? = null
  private var myStatusMessage: StatusMessage? = null

  fun registerAndGetInitNotifications(content: NotificationContent, notifications: MutableList<Notification>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    myContent = content
  }

  fun addNotification(project: Project,
                      notification: Notification,
                      appNotifications: List<Notification>,
                      runnables: MutableList<Runnable>) {
    if (myContent == null) {
      myNotifications.add(notification)

      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)

      runnables.add(Runnable {
        updateToolWindow(project, notification, notifications, false)
      })
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.add(notification) } })
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

  fun expire(project: Project, notification: Notification, runnables: MutableList<Runnable>) {
    myNotifications.remove(notification)
    if (myContent == null) {
      runnables.add(Runnable {
        updateToolWindow(project, null, myNotifications, false)
      })
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.expire(notification) } })
    }
  }

  fun expireAll(project: Project, notifications: MutableList<Notification>, runnables: MutableList<Runnable>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    if (myContent == null) {
      updateToolWindow(project, null, emptyList(), false)
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.expire(null) } })
    }
  }

  fun clearAll(project: Project) {
    myNotifications.clear()
    if (myContent == null) {
      updateToolWindow(project, null, emptyList(), true)
    }
    else {
      UIUtil.invokeLaterIfNeeded { myContent!!.clearAll() }
    }
  }

  private fun updateToolWindow(project: Project,
                               stateNotification: Notification?,
                               notifications: List<Notification>,
                               closeBalloons: Boolean) {
    UIUtil.invokeLaterIfNeeded {
      if (project.isDisposed) {
        return@invokeLaterIfNeeded
      }

      setStatusMessage(project, stateNotification)

      if (closeBalloons) {
        project.closeAllBalloons()
      }

      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
      toolWindow?.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(notifications))
    }
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