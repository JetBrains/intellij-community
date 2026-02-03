// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a task that can be either suspendable or non-suspendable.
 */
@ApiStatus.Internal
@Serializable
sealed interface TaskSuspension {

  /**
   * This object indicates that a task cannot be paused.
   */
  @Serializable
  object NonSuspendable : TaskSuspension

  /**
   * Represents a suspendable task, which can be paused and resumed.
   *
   * @property suspendText A text message explaining the reason for the suspension,
   *                       which is displayed in the progress bar.
   */
  @Serializable
  data class Suspendable(@NlsContexts.ProgressText val suspendText: String) : TaskSuspension
}
