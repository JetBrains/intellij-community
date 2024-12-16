// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.NonExtendable
import kotlin.coroutines.CoroutineContext

/**
 * Interface for suspending and resuming running tasks.
 * To assign suspender to a task use [withBackgroundProgress]
 */
@NonExtendable
interface TaskSuspender {

  val state: Flow<TaskSuspenderState>

  /**
   * Checks if the tasks assigned to this suspender are currently paused.
   *
   * @return true if the tasks are paused, false otherwise
   */
  fun isPaused(): Boolean

  /**
   * Pauses the execution of tasks assigned to this suspender.
   * To ensure the task is going to be paused, use [com.intellij.openapi.progress.checkCanceled]
   *
   * @param reason a message, explaining the reason for the pause, to be displayed in the progress bar.
   */
  fun pause(reason: @ProgressText String? = null)

  /**
   * This method resumes the operation of the previously paused tasks.
   */
  fun resume()

  /**
   * Converts this instance to a CoroutineContext element.
   *
   * This function is useful for integrating existing types or values
   * into coroutine-based APIs by converting them to coroutine context elements.
   *
   * @return a CoroutineContext element representing this instance.
   */
  fun asContextElement(): CoroutineContext

  companion object {
    /**
     * Creates a new instance of [TaskSuspender] which can be used to suspend and resume tasks.
     *
     * @param coroutineScope The scope to define suspender's lifetime
     * @param suspendedText a default message displayed in UI for paused tasks
     * @return a new instance of [TaskSuspender].
     */
    @JvmStatic
    fun suspendable(coroutineScope: CoroutineScope, suspendedText: @ProgressText String): TaskSuspender {
      return TaskSuspenderImpl(coroutineScope, suspendedText)
    }
  }
}
