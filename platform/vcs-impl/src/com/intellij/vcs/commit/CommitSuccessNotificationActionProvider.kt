// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CommitSuccessNotificationActionProvider {
  fun getActions(committer: VcsCommitter, notification: CommitNotification): List<NotificationAction>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<CommitSuccessNotificationActionProvider> =
      ExtensionPointName.create("com.intellij.vcs.commitSuccessNotificationActionProvider")
  }
}
