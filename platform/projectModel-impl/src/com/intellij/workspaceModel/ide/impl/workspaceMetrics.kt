// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics
import com.intellij.util.concurrency.SynchronizedClearableLazy

val workspaceModelMetrics: WorkspaceModelMetrics by lazy { WorkspaceModelMetrics.instance.value }

class WorkspaceModelMetrics : SharedMetrics(WorkspaceModel) {
  companion object {
    val instance: SynchronizedClearableLazy<WorkspaceModelMetrics> = SynchronizedClearableLazy { WorkspaceModelMetrics() }
  }
}
