// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.notification.impl.NotificationIdsHolder


internal object GitLabNotificationIds {
  const val GL_NOTIFICATION_UPLOAD_FILE_ERROR = "gitlab.upload.file.action.error"
}

class GitLabNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    GitLabNotificationIds.GL_NOTIFICATION_UPLOAD_FILE_ERROR,
  )
}