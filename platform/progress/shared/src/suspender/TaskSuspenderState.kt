// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.util.NlsContexts.ProgressText

/**
 * Represents the states of [TaskSuspender], defining whether the tasks are currently active or paused.
 */
sealed class TaskSuspenderState {
  /**
   * Represents the active state of a [TaskSuspender], indicating that tasks are currently running
   */
  object Active : TaskSuspenderState()

  /**
   * Represents the paused state of a [TaskSuspender], indicating that tasks are temporarily suspended.
   *
   * @property suspendedReason An optional human-readable message explaining the reason for the pause,
   * which can be displayed in the progress bar.
   */
  class Paused(val suspendedReason: @ProgressText String?) : TaskSuspenderState()
}
