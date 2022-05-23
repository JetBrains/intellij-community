// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

fun catchNotificationText(project: Project, action: () -> Unit): String? {
    val notifications = catchNotifications(project, action).ifEmpty { return null }
    return notifications.single().content
}

fun catchNotifications(project: Project, action: () -> Unit): List<Notification> {
    val myDisposable = Disposer.newDisposable()
    try {
        val notifications = mutableListOf<Notification>()
        val connection = project.messageBus.connect(myDisposable)
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })

        action()
        connection.deliverImmediately()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        return notifications
    } finally {
        Disposer.dispose(myDisposable)
    }
}

val Notification.asText: String get() = "Title: '$title'\nContent: '$content'"
val List<Notification>.asText: String
    get() = sortedBy { it.content }.joinToString(separator = "\n-----\n", transform = Notification::asText)
