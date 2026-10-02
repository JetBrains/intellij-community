// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityTracker
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class VfsActivityTracker : ActivityTracker {

  override val presentableName: @Nls String = IdeBundle.message("vfs.activity.tracker.name")

  override suspend fun isInProgress(project: Project): Boolean {
    return RefreshQueueImpl.isRefreshInProgress
  }

  override suspend fun awaitConfiguration(project: Project) {
    while (isInProgress(project)) {
      delay(50)
    }
  }
}