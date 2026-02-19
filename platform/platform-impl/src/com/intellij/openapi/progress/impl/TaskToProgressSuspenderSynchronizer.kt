// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal class TaskToProgressSuspenderSynchronizer(
  coroutineScope: CoroutineScope,
  private val taskSuspender: TaskSuspender,
  private val progressSuspender: ProgressSuspender,
) : ProgressSuspenderTracker.SuspenderListener {

  private val stateChangedLock = Any()
  private val isStateChangeInProgress = AtomicBoolean(false)

  private val subscription = coroutineScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
    taskSuspender.state.collectLatest { state ->
      synchronized(stateChangedLock) {
        isStateChangeInProgress.set(true)

        try {
          if (state is TaskSuspenderState.Paused) {
            progressSuspender.suspendProcess(state.suspendedReason)
          }
          else {
            progressSuspender.resumeProcess()
          }
        }
        finally {
          isStateChangeInProgress.set(false)
        }
      }
    }
  }

  init {
    ProgressSuspenderTracker.getInstance().startTracking(progressSuspender, this)
  }

  fun stop() {
    subscription.cancel()
    ProgressSuspenderTracker.getInstance().stopTracking(progressSuspender)
  }

  override fun onStateChanged(suspender: ProgressSuspender) {
    synchronized(stateChangedLock) {
      // Don't update taskSuspender if state change was initiated by taskSuspender itself
      if (isStateChangeInProgress.get()) return@synchronized

      if (suspender.isSuspended) {
        taskSuspender.pause(suspender.suspendedText)
      }
      else {
        taskSuspender.resume()
      }
    }
  }
}
