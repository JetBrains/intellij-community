// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the status of a task
 */
@ApiStatus.Internal
@Serializable
sealed interface TaskStatus {

  enum class Source {
    SYSTEM,
    USER,
  }

  /**
   * Represents the origin of a [TaskStatus] change.
   *
   * The [Source] enum defines two possible origins:
   * - [Source.SYSTEM]: Indicates the operation or event was initiated or triggered by the system itself.
   * - [Source.USER]: Indicates the operation or event was initiated or triggered by a user (via UI)
   */
  val source: Source

  /**
   * Indicates that a task is currently in progress.
   * The task can be suspended ([Paused]), canceled ([Canceled]) or finish without status change
   */
  @Serializable
  data class Running(override val source: Source) : TaskStatus

  /**
   * Indicates that a task has been temporarily paused.
   *
   * A task in this state may be resumed ([Running]) or canceled ([Canceled]).
   * It implies that the task has been running before suspension.
   *
   * @property reason a message, explaining the reason for the pause, to be displayed in the progress bar.
   */
  @Serializable
  data class Paused(@NlsContexts.ProgressText val reason: String? = null, override val source: Source) : TaskStatus

  /**
   * Indicates that a task has been requested to stop.
   * Not that the task might still be running until it can be safely aborted.
   *
   * A task in this state cannot be resumed or paused anymore.
   */
  @Serializable
  data class Canceled(override val source: Source) : TaskStatus
}
