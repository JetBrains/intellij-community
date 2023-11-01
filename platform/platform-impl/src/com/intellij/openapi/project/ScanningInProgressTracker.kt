// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.observation.ActivityInProgressTracker
import kotlinx.coroutines.flow.first

class ScanningInProgressTracker : ActivityInProgressTracker {
  override val presentableName: String = "scanning"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<UnindexedFilesScannerExecutor>().isRunning.value
  }

  override suspend fun awaitConfiguration(project: Project) {
    project.serviceAsync<UnindexedFilesScannerExecutor>().isRunning.first { !it }
  }
}