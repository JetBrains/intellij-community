// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the status of a task
 */
@ApiStatus.Internal
@Serializable
enum class TaskStatus {
  /**
   * Indicates that a task is currently in progress.
   * The task can be suspended ([PAUSED]), canceled ([CANCELED]) or finish without status change
   */
  RUNNING,

  /**
   * Indicates that a task has been temporarily paused.
   *
   * A task in this state may be resumed ([RUNNING]) or canceled ([CANCELED]).
   * It implies that the task has been running before suspension.
   */
  PAUSED,

  /**
   * Indicates that a task has been requested to stop.
   * Not that the task might still be running until it can be safely aborted.
   *
   * A task in this state cannot be resumed or paused anymore.
   */
  CANCELED
}
