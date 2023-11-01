// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityInProgressTracker
import kotlinx.coroutines.delay
import org.jetbrains.annotations.Nls

class VfsActivityInProgressTracker : ActivityInProgressTracker {

  override val presentableName: @Nls String = "vfs"

  override suspend fun isInProgress(project: Project): Boolean {
    return RefreshQueueImpl.isRefreshInProgress()
  }

  override suspend fun awaitConfiguration(project: Project) {
    while (isInProgress(project)) {
      delay(50)
    }
  }
}