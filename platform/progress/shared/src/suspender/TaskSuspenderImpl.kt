// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.progress.CoroutineSuspenderImpl
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * An internal implementation of the [TaskSuspender] interface that uses a [CoroutineSuspenderImpl]
 * to handle the suspension and resumption of tasks.
 *
 * NOTE: Use [TaskSuspender.suspendable] instead of creating suspender manually
 *
 * @constructor Creates an instance with a default suspended text and an optional coroutine suspender.
 * @param coroutineScope The scope to define suspender's lifetime
 * @param defaultSuspendedReason The default message to be displayed when the tasks are suspended.
 * @param coroutineSuspender The coroutine suspender to manage the state
 */
@ApiStatus.Internal
class TaskSuspenderImpl(
  val coroutineScope: CoroutineScope,
  @ProgressText val defaultSuspendedReason: String,
  private val coroutineSuspender: CoroutineSuspenderImpl = CoroutineSuspenderImpl(true),
) : TaskSuspender {

  private val _state = MutableStateFlow<TaskSuspenderState>(TaskSuspenderState.Active)
  override val state: Flow<TaskSuspenderState> = _state

  private val suspenderLock = Any()

  init {
    coroutineScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
      coroutineSuspender.isPaused.collectLatest { paused ->
        if (paused) {
          // don't erase the original reason if paused
          _state.compareAndSet(TaskSuspenderState.Active, TaskSuspenderState.Paused(defaultSuspendedReason))
        }
        else {
          _state.update { TaskSuspenderState.Active }
        }
      }
    }
  }

  override fun isPaused(): Boolean {
    return _state.value is TaskSuspenderState.Paused
  }

  override fun pause(reason: @ProgressText String?) {
    synchronized(suspenderLock) {
      if (_state.compareAndSet(TaskSuspenderState.Active, TaskSuspenderState.Paused(reason ?: defaultSuspendedReason))) {
        coroutineSuspender.pause()
      }
    }
  }

  override fun resume() {
    synchronized(suspenderLock) {
      val oldState = _state.getAndUpdate { TaskSuspenderState.Active }
      if (oldState is TaskSuspenderState.Paused) {
        coroutineSuspender.resume()
      }
    }
  }

  internal fun getCoroutineContext(): CoroutineContext {
    return TaskSuspenderElement(this) + coroutineSuspender.asContextElement()
  }
}