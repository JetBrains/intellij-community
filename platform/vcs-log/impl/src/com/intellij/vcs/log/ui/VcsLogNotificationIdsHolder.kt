// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.notification.impl.NotificationIdsHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsLogNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      LOG_NOT_AVAILABLE,
      FATAL_ERROR,
      COMMIT_NOT_FOUND,
      NAVIGATION_ERROR,
      FILE_HISTORY_ACTION_LOAD_DETAILS_ERROR
    )
  }

  companion object {
    const val LOG_NOT_AVAILABLE = "vcs.log.not.available"
    const val FATAL_ERROR = "vcs.log.fatal.error"
    const val COMMIT_NOT_FOUND = "vcs.log.commit.not.found"
    const val NAVIGATION_ERROR = "vcs.log.navigation.error"
    const val FILE_HISTORY_ACTION_LOAD_DETAILS_ERROR = "file.history.load.details.error"
  }
}
