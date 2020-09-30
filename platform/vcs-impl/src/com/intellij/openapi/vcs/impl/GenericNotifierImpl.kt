// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.GuiUtils.invokeLaterIfNeeded
import com.intellij.util.ui.UIUtil
import java.util.*
import javax.swing.event.HyperlinkEvent

abstract class GenericNotifierImpl<T, Key>(@JvmField protected val myProject: Project,
                                           private val myGroupId: String,
                                           @NlsContexts.NotificationTitle private val myTitle: String,
                                           private val myType: NotificationType) {
  private val myState = HashMap<Key, MyNotification>()
  private val myListener = MyListener()
  private val myLock = Any()

  protected val allCurrentKeys get() = synchronized(myLock) { myState.keys.toList() }

  val isEmpty get() = synchronized(myLock) { myState.isEmpty() }

  protected abstract fun ask(obj: T, description: String?): Boolean
  protected abstract fun getKey(obj: T): Key
  protected abstract fun getNotificationContent(obj: T): @NlsContexts.NotificationContent String

  protected fun getStateFor(key: Key) = synchronized(myLock) { myState.containsKey(key) }

  fun clear() {
    val notifications = synchronized(myLock) {
      val currentNotifications = myState.values.toList()
      myState.clear()
      currentNotifications
    }
    invokeLaterIfNeeded(Runnable {
      for (notification in notifications) {
        notification.expire()
      }
    }, ModalityState.NON_MODAL, myProject.disposed)
  }

  private fun expireNotification(notification: MyNotification) = UIUtil.invokeLaterIfNeeded { notification.expire() }

  open fun ensureNotify(obj: T): Boolean {
    val notification = synchronized(myLock) {
      val key = getKey(obj)
      if (myState.containsKey(key)) return false

      val objNotification = MyNotification(myGroupId, myTitle, getNotificationContent(obj), myType, myListener, obj)
      myState[key] = objNotification
      objNotification
    }
    if (onFirstNotification(obj)) {
      removeLazyNotification(obj)
      return true
    }
    Notifications.Bus.notify(notification, myProject)
    return false
  }

  protected open fun onFirstNotification(obj: T) = false

  fun removeLazyNotificationByKey(key: Key) {
    val notification = synchronized(myLock) { myState.remove(key) }
    if (notification != null) {
      expireNotification(notification)
    }
  }

  fun removeLazyNotification(obj: T) = removeLazyNotificationByKey(getKey(obj))

  private inner class MyListener : NotificationListener {
    override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
      val concreteNotification = notification as GenericNotifierImpl<T, Key>.MyNotification
      val obj = concreteNotification.obj
      if (ask(obj, event.description)) {
        synchronized(myLock) {
          myState.remove(getKey(obj))
        }
        expireNotification(concreteNotification)
      }
    }
  }

  protected inner class MyNotification(groupId: String,
                                       @NlsContexts.NotificationTitle title: String,
                                       @NlsContexts.NotificationContent content: String,
                                       type: NotificationType,
                                       listener: NotificationListener?,
                                       val obj: T) : Notification(groupId, title, content, type, listener) {

    override fun expire() {
      super.expire()
      synchronized(myLock) {
        myState.remove(getKey(obj))
      }
    }
  }
}
