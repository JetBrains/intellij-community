package com.intellij.settingsSync.core.config

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.settingsSync.core.NOTIFICATION_GROUP
import com.intellij.settingsSync.core.SettingsSyncBundle.message

internal class SettingsSyncRemoveDataNotifications {
  companion object {
    fun getFailedToDeleteSyncDataNotification(userAccountName: String, removeRemoteDataAction: () -> Unit, contactSupportFunction: (() -> Unit)?): Notification {
      val notification = NotificationGroupManager
        .getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(
          message("sync.remove.data.error.message.title"),
          message("notification.failed.delete.sync.data.content", userAccountName),
          NotificationType.ERROR
        )
      notification.isSuggestionType = true
      notification.addAction(NotificationAction.createSimple(message("sync.remove.data.error.message.action.delete")) {
        notification.expire()
        removeRemoteDataAction.invoke()
      })
      if (contactSupportFunction != null) {
        notification.addAction(NotificationAction.createSimple(message("sync.remove.data.error.message.action.support")) {
          notification.expire()
          contactSupportFunction.invoke()
        })
      }
      return notification
    }

    fun getSuccessfullyDeletedSyncDataNotification(userAccountName: String): Notification {
      val notification = NotificationGroupManager
        .getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(
          message("notification.success.delete.sync.data.content", userAccountName),
          NotificationType.INFORMATION
        )
      notification.setIcon(AllIcons.Status.Success)
      return notification
    }
  }
}