// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderState
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference

/**
 * [BridgeTaskSuspender] is responsible for managing the suspension and resumption of tasks,
 * bridging the gap between [ProgressSuspender] and [TaskSuspender]. This class keeps track of
 * the current suspension state and notifies registered listeners about any changes.
 *
 * @param indicator The [ProgressIndicator] associated with the tasks to be suspended or resumed.
 */
@Suppress("DEPRECATION")
@Deprecated("Use TaskSuspenderImpl instead")
internal class BridgeTaskSuspender(indicator: ProgressIndicator) : TaskSuspender, ProgressSuspenderTracker.IndicatorListener {
  private val progressSuspender = AtomicReference<ProgressSuspender?>(null)

  private val _isSuspendable = MutableStateFlow<TaskSuspension>(TaskSuspension.NonSuspendable)
  val isSuspendable: StateFlow<TaskSuspension> = _isSuspendable

  private val _state = MutableStateFlow<TaskSuspenderState>(TaskSuspenderState.Active)
  override val state: Flow<TaskSuspenderState> = _state

  private val suspenderLock = Any()

  init {
    val suspender = ProgressSuspender.getSuspender(indicator)
    if (suspender != null) {
      progressSuspender.set(suspender)
      _isSuspendable.value = TaskSuspension.Suspendable(suspender.suspendedText)
      _state.value = if (suspender.isSuspended) {
        TaskSuspenderState.Paused(suspender.suspendedText)
      }
      else {
        TaskSuspenderState.Active
      }

      // Tracking changes in the suspender state (onStateChanged)
      ProgressSuspenderTracker.getInstance().startTracking(suspender, this)
    }

    // Tracking the appearance and disappearance of a suspender related to the indicator (suspenderAdded, suspenderRemoved)
    ProgressSuspenderTracker.getInstance().startTracking(indicator, this)
  }

  override fun isPaused(): Boolean {
    return _state.value is TaskSuspenderState.Paused
  }

  override fun pause(reason: @ProgressText String?) {
    synchronized(suspenderLock) {
      if (isSuspendable.value is TaskSuspension.NonSuspendable) return@synchronized

      if (_state.compareAndSet(TaskSuspenderState.Active, TaskSuspenderState.Paused(reason))) {
        progressSuspender.get()?.suspendProcess(reason)
      }
    }
  }

  override fun resume() {
    synchronized(suspenderLock) {
      val oldState = _state.getAndUpdate { TaskSuspenderState.Active }
      if (oldState is TaskSuspenderState.Paused) {
        progressSuspender.get()?.resumeProcess()
      }
    }
  }

  override fun suspenderAdded(suspender: ProgressSuspender) {
    synchronized(suspenderLock) {
      progressSuspender.set(suspender)
      _isSuspendable.value = TaskSuspension.Suspendable(suspender.suspendedText)
      onStateChanged(suspender)
    }
  }

  override fun suspenderRemoved() {
    synchronized(suspenderLock) {
      progressSuspender.set(null)
      _isSuspendable.value = TaskSuspension.NonSuspendable
      _state.update { TaskSuspenderState.Active }
    }
  }

  override fun onStateChanged(suspender: ProgressSuspender) {
    if (suspender.isSuspended) {
      _state.compareAndSet(TaskSuspenderState.Active, TaskSuspenderState.Paused(suspender.suspendedText))
    }
    else {
      _state.update { TaskSuspenderState.Active }
    }
  }
}