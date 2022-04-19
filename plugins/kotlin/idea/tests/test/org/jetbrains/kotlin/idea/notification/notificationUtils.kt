// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase.assertNull

fun catchNotificationText(project: Project, action: () -> Unit): String? {
  val myDisposable = Disposer.newDisposable()
  try {
    var notificationText: String? = null
    val connection = project.messageBus.connect(myDisposable)
    connection.subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        assertNull(notificationText)
        notificationText = notification.content
      }
    })

    action()
    connection.deliverImmediately()
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    return notificationText
  } finally {
    Disposer.dispose(myDisposable)
  }
}
