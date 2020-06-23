// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object FilePredictionNotifications {
  private val NOTIFICATION_GROUP =
    NotificationGroup.balloonGroup("NextFilePrediction", FilePredictionBundle.message("file.prediction.notification.group"))

  fun showWarning(project: Project?, message: String) {
    showNotification(project, NotificationType.WARNING, message)
  }

  fun showInfo(project: Project?, message: String) {
    showNotification(project, NotificationType.INFORMATION, message)
  }

  private fun showNotification(project: Project?, type: NotificationType, message: String) {
    val title = FilePredictionBundle.message("file.prediction.notification.group.title")
    NOTIFICATION_GROUP.createNotification(title, message, type).notify(project)
  }

}