// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.progress.CoroutineSuspenderElement
import com.intellij.openapi.progress.CoroutineSuspenderImpl
import com.intellij.openapi.progress.CoroutineSuspenderState
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * An internal implementation of the [TaskSuspender] interface that uses a [CoroutineSuspenderImpl]
 * to handle the suspension and resumption of tasks.
 *
 * NOTE: Use [TaskSuspender.suspendable] instead of creating suspender manually
 *
 * @constructor Creates an instance with a default suspended text and an optional coroutine suspender.
 * @param defaultSuspendedText The default message to be displayed when the tasks are suspended.
 * @param coroutineSuspender The coroutine suspender to manage the state
 */
@ApiStatus.Internal
class TaskSuspenderImpl(
  @NlsContexts.ProgressText val defaultSuspendedText: String,
  private val coroutineSuspender: CoroutineSuspenderImpl = CoroutineSuspenderImpl(true),
) : TaskSuspender {
  @Volatile
  private var temporarySuspendedText: @NlsContexts.ProgressText String? = null

  sealed class TaskSuspenderState {
    object Active : TaskSuspenderState()
    class Paused(@NlsContexts.ProgressText val reason: String?) : TaskSuspenderState()
  }

  val state: Flow<TaskSuspenderState> = coroutineSuspender.state
    .map {
      when (it) {
        CoroutineSuspenderState.Active -> {
          // CoroutineSuspender can be paused and resumed independently of TaskSuspender,
          // so we need to reset temporarySuspendedText both in resume method and here
          temporarySuspendedText = null
          TaskSuspenderState.Active
        }
        is CoroutineSuspenderState.Paused -> TaskSuspenderState.Paused(temporarySuspendedText)
      }
    }

  override fun isPaused(): Boolean {
    return coroutineSuspender.isPaused()
  }

  override fun pause(@NlsContexts.ProgressText reason: String?) {
    temporarySuspendedText = reason
    coroutineSuspender.pause()
  }

  override fun resume() {
    temporarySuspendedText = null
    coroutineSuspender.resume()
  }

  override fun asContextElement(): CoroutineContext {
    return coroutineSuspender.asContextElement()
  }
}