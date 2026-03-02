// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction

interface CommitExceptionWithActions {
  /**
   * Returns notification actions for this exception.
   *
   * Notification instance can be used to expire the notification when certain conditions change (e.g., repository HEAD changes, making actions invalid)
   */
  fun getActions(notification: Notification): List<NotificationAction>

  /**
   * Determines whether the notification should have a "Show details in console" action.
   * For some exceptions it doesn't make sense to show it.
   */
  val shouldAddShowDetailsAction: Boolean
    get() = true
}