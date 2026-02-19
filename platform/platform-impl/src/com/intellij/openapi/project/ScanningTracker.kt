// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.observation.ActivityTracker
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ScanningTracker : ActivityTracker {
  override val presentableName: String = "scanning"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<UnindexedFilesScannerExecutor>().isRunning.value ||
           project.serviceAsync<UnindexedFilesScannerExecutor>().hasQueuedTasks
  }

  override suspend fun awaitConfiguration(project: Project) {
    project.serviceAsync<UnindexedFilesScannerExecutor>().isRunning.first { !it }
  }
}