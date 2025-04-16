// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle

@Service(Service.Level.PROJECT)
internal class ComposeResourcesNotificationService(private val project: Project) {

  fun notifyError(
    @NotificationTitle title: String,
    @NotificationContent content: String,
  ) {
    notify(title, content, NotificationType.ERROR)
  }

  private fun notify(
    @NotificationTitle title: String,
    @NotificationContent content: String,
    type: NotificationType,
  ) {
    val notification = Notification(COMPOSE_RESOURCES_NOTIFICATION_GROUP_ID, title, content, type)
    notification.notify(project)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ComposeResourcesNotificationService = project.service()

    private const val COMPOSE_RESOURCES_NOTIFICATION_GROUP_ID = "ComposeResources"
  }
}
