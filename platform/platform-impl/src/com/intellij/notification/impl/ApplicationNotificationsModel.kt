// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.impl.ApplicationNotificationsModel.STATE_CHANGED
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.BalloonLayoutImpl
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ApplicationNotificationsModel {
  const val EVENT_REQUESTOR: String = "Internal notification event requestor"

  private val notifications = ArrayList<Notification>()
  private val projectToModel = HashMap<Project, ProjectNotificationsModel>()
  private val dataGuard = Object()

  @JvmField
  @Topic.AppLevel
  val STATE_CHANGED: Topic<StateEventListener> = Topic.create("NOTIFICATION_MODEL_CHANGED",
                                                              StateEventListener::class.java,
                                                              Topic.BroadcastDirection.NONE)

  @JvmStatic
  fun addNotification(project: Project?, notification: Notification) {
    if (!notification.canShowFor(project) || !NotificationsConfigurationImpl.getSettings(notification.groupId).isShouldLog) {
      return
    }

    if (project == null) {
      addApplicationNotification(notification)
    }
    else {
      addProjectNotification(project, notification)
    }
  }

  private fun addApplicationNotification(notification: Notification) {
    if (projectToModel.isEmpty()) {
      synchronized(dataGuard) {
        notifications.add(notification)
      }
    }
    else {
      val callbacks = mutableListOf<ProjectCallback>()
      synchronized(dataGuard) {
        for ((project, model) in projectToModel.entries) {
          val callback = model.addNotification(project, notification)
          callbacks.add(callback.inProject(project))
        }
      }
      callbacks.forEach(ProjectCallback::invokeLaterIfNeeded)
    }
  }

  private fun addProjectNotification(project: Project, notification: Notification) {
    val callback = synchronized(dataGuard) {
      val model = projectToModel.getOrPut(project) { newProjectModel(project) }
      model.addNotification(project, notification)
    }
    callback.inProject(project).invokeLaterIfNeeded()
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
      projectToModel[project]?.getUnreadNotifications() ?: notifications
    }
  }

  fun markAllRead(project: Project) {
    val callback = synchronized(dataGuard) {
      projectToModel[project]?.markAllRead()
    } ?: return

    callback.inProject(project).invokeLaterIfNeeded()
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
  fun remove(notification: Notification) {
    val callbacks = synchronized(dataGuard) {
      notifications.remove(notification)

      val callbacks = mutableListOf<ProjectCallback>()
      for ((project, model) in projectToModel) {
        val callback = model.remove(project, notification)
        callbacks.add(callback.inProject(project))
      }
      callbacks
    }
    callbacks.forEach(ProjectCallback::invokeLaterIfNeeded)
  }

  fun clearAll(project: Project?) {
    if (project == null) {
      synchronized(dataGuard) {
        notifications.clear()
      }
      return
    }
    val callback = synchronized(dataGuard) {
      notifications.clear()
      projectToModel[project]?.clearAll(project)
    } ?: return
    callback.inProject(project).invokeLaterIfNeeded()
  }

  fun clearTimeline(project: Project) {
    val callback = synchronized(dataGuard) {
      notifications.removeAll {
        !it.isSuggestionType
      }
      projectToModel[project]?.clearTimeline(project)
    } ?: return
    callback.inProject(project).invokeLaterIfNeeded()
  }

  fun expireAll() {
    val callback = synchronized(dataGuard) {
      val allNotifications = notifications.toMutableList()
      notifications.clear()

      val callbacks = mutableListOf<ProjectCallback>()
      for ((project, model) in projectToModel) {
        val (projectNotifications, callback) = model.expireAll(project)
        allNotifications.addAll(projectNotifications)
        callbacks.add(callback.inProject(project))
      }

      Runnable {
        callbacks.forEach(ProjectCallback::invokeLaterIfNeeded)

        for (notification in allNotifications) {
          notification.expire()
        }
      }
    }
    callback.run()
  }

  fun fireNotificationsPanelVisible(project: Project) {
    project.closeAllBalloons()
  }

  fun register(project: Project, listener: ProjectNotificationsModelListener) {
    val callback = synchronized(dataGuard) {
      val initNotifications = notifications.toMutableList()
      notifications.clear()

      projectToModel.getOrPut(project) {
        newProjectModel(project)
      }.register(project, listener, initNotifications)
    }

    callback.inProject(project).invokeLaterIfNeeded()
  }

  fun unregister(project: Project) {
    synchronized(dataGuard) {
      projectToModel.remove(project)
    }
  }

  private fun newProjectModel(project: Project): ProjectNotificationsModel {
    Disposer.register(project) {
      synchronized(dataGuard) {
        projectToModel.remove(project)
      }
    }
    return ProjectNotificationsModel()
  }

  fun interface StateEventListener {
    fun stateChanged()
  }
}

private class ProjectNotificationsModel {
  private val myNotifications = mutableListOf<Notification>()
  private val unreadNotifications = mutableListOf<Notification>()
  private var listener: ProjectNotificationsModelListener? = null
  private var statusMessage: StatusMessage? = null

  fun register(
    project: Project,
    newListener: ProjectNotificationsModelListener,
    appInitNotifications: List<Notification>,
  ): Runnable {
    val initNotifications = buildList {
      addAll(appInitNotifications)
      addAll(myNotifications)
    }
    myNotifications.clear()
    listener = newListener
    return Runnable {
      newListener.add(initNotifications)
      setStatusMessage(project, initNotifications.lastOrNull())
      fireStateChanged()
    }
  }

  fun addNotification(project: Project, notification: Notification): Runnable {
    myNotifications.add(notification)
    unreadNotifications.add(notification)
    val listener = listener
    return Runnable {
      listener?.add(notification)
      setStatusMessage(project, notification)
      fireStateChanged()
    }
  }

  fun getUnreadNotifications(): List<Notification> = unreadNotifications.toList()

  fun isEmptyContent(): Boolean {
    return listener == null || listener!!.isEmpty()
  }

  fun getNotifications(appNotifications: List<Notification>): List<Notification> {
    if (listener == null) {
      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)
      return notifications
    }
    return listener!!.getNotifications()
  }

  fun markAllRead(): Runnable {
    unreadNotifications.clear()
    val listener = listener
    return Runnable {
      listener?.clearUnreadStates()
      fireStateChanged()
    }
  }

  fun remove(project: Project, notification: Notification): Runnable {
    myNotifications.remove(notification)
    unreadNotifications.remove(notification)
    val listener = listener
    return Runnable {
      listener?.remove(notification)
      val leftOverNotifications = listener?.getNotifications().orEmpty()
      val newStatus = leftOverNotifications.findLast { it.isImportant || it.isImportantSuggestion }
      setStatusMessage(project, newStatus)
      fireStateChanged()
    }
  }

  fun expireAll(project: Project): Pair<List<Notification>, Runnable> {
    val notifications = myNotifications.toList()
    myNotifications.clear()
    unreadNotifications.clear()
    val listener = listener
    return notifications to Runnable {
      listener?.expireAll()
      setStatusMessage(project, null)
      fireStateChanged()
    }
  }

  fun clearTimeline(project: Project): Runnable {
    myNotifications.removeAll {
      !it.isSuggestionType
    }
    unreadNotifications.removeAll {
      !it.isSuggestionType
    }
    val listener = listener
    return Runnable {
      project.closeAllBalloons()
      listener?.clearTimeline()
      setStatusMessage(project, null)
      fireStateChanged()
    }
  }

  fun clearAll(project: Project): Runnable {
    myNotifications.clear()
    unreadNotifications.clear()
    val listener = listener
    return Runnable {
      project.closeAllBalloons()
      listener?.clearAll()
      setStatusMessage(project, null)
      fireStateChanged()
    }
  }

  fun getStatusMessage(): StatusMessage? {
    return statusMessage
  }

  private fun setStatusMessage(project: Project, notification: Notification?) {
    if ((statusMessage == null && notification == null) || (statusMessage != null && statusMessage!!.notification === notification)) {
      return
    }
    statusMessage = if (notification == null) {
      null
    }
    else {
      StatusMessage(notification, NotificationsUtil.buildStatusMessage(notification), notification.timestamp)
    }
    StatusBar.Info.set("", project, ApplicationNotificationsModel.EVENT_REQUESTOR)
  }

  // fired when [unreadNotifications] list changes
  private fun fireStateChanged() {
    ApplicationManager.getApplication().messageBus.syncPublisher(STATE_CHANGED).stateChanged()
  }
}

@Internal
@JvmRecord
data class StatusMessage(val notification: Notification, val text: @NlsContexts.StatusBarText String, val stamp: Long)

private fun Project.closeAllBalloons() {
  val ideFrame = WindowManager.getInstance().getIdeFrame(this)
  val balloonLayout = ideFrame!!.balloonLayout as BalloonLayoutImpl
  balloonLayout.closeAll()
}

private fun Runnable.inProject(project: Project): ProjectCallback = ProjectCallback(project, this)

private class ProjectCallback(private val project: Project, private val runnable: Runnable) {
  fun invokeLaterIfNeeded() {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      runnable.run()
    }
    else {
      app.invokeLater(runnable::run, project.disposed)
    }
  }
}