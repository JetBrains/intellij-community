// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

internal object FilePredictionNotifications {
  private val NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("NextFilePrediction")

  fun showWarning(project: Project?, @Nls message: String) {
    showNotification(project, NotificationType.WARNING, message)
  }

  fun showInfo(project: Project?, @Nls message: String) {
    showNotification(project, NotificationType.INFORMATION, message)
  }

  private fun showNotification(project: Project?, type: NotificationType, @Nls message: String) {
    val title = FilePredictionBundle.message("file.prediction.notification.group.title")
    NOTIFICATION_GROUP.createNotification(title, message, type).notify(project)
  }

}