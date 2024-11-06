// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CommitNotification(
  groupId: String,
  title: @NotificationTitle String,
  content: @NotificationContent String,
  type: NotificationType = NotificationType.INFORMATION,
) : Notification(groupId, title, content, type) {
  fun expirePreviousAndNotify(project: Project) {
    NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(CommitNotification::class.java, project).forEach(CommitNotification::expire)
    notify(project)
  }
}