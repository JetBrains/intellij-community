// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.observation.ActivityInProgressPredicate
import com.intellij.openapi.components.serviceAsync

class ScanningInProgressPredicate : ActivityInProgressPredicate {
  override val presentableName: String = "scanning"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<UnindexedFilesScannerExecutor>().isRunning.value
  }
}