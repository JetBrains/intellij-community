// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle

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
        ApplicationManager.getApplication().invokeAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
        return notifications
    } finally {
        Disposer.dispose(myDisposable)
    }
}

val Notification.asText: String get() = "Title: '$title'\nContent: '$content'"
fun List<Notification>.asText(filterNotificationAboutNewKotlinVersion: Boolean = true): String =
    sortedBy { it.content }
        .filter {
            !filterNotificationAboutNewKotlinVersion ||
                    !it.content.contains(
                        KotlinMigrationBundle.message(
                            "kotlin.external.compiler.updates.notification.content.0",
                            KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion
                        )
                    )
        }
        .joinToString(separator = "\n-----\n", transform = Notification::asText)
