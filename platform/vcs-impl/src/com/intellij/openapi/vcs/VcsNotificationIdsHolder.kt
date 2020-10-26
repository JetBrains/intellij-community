// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.notification.impl.NotificationIdsHolder

class VcsNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(IGNORED_TO_EXCLUDE_SYNCHRONIZATION,
                  EXTERNALLY_ADDED_FILES,
                  PROJECT_CONFIGURATION_FILES_ADDED,
                  MANAGE_IGNORE_FILES)
  }

  companion object {
    const val IGNORED_TO_EXCLUDE_SYNCHRONIZATION = "ignored.to.exclude.synchronization.notification"
    const val EXTERNALLY_ADDED_FILES = "externally.added.files.notification"
    const val PROJECT_CONFIGURATION_FILES_ADDED = "project.configuration.files.added.notification"
    const val MANAGE_IGNORE_FILES = "manage.ignore.files.notification"
  }
}

