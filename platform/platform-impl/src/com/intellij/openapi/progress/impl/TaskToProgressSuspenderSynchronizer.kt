// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class TaskToProgressSuspenderSynchronizer(
  coroutineScope: CoroutineScope,
  taskSuspender: TaskSuspender,
  private val progressSuspender: ProgressSuspender,
) {
  private val stateListenerJob: Job

  init {
    ProgressSuspenderTracker.getInstance().startTracking(progressSuspender, ProgressToTaskListener(taskSuspender))
    stateListenerJob = coroutineScope.launch {
      if (taskSuspender !is TaskSuspenderImpl) {
        LOG.warn("Unexpected task suspender type: ${taskSuspender::class.java}")
        return@launch
      }

      taskSuspender.state.collect { state ->
        when (state) {
          TaskSuspenderImpl.TaskSuspenderState.Active -> progressSuspender.resumeProcess()
          is TaskSuspenderImpl.TaskSuspenderState.Paused -> progressSuspender.suspendProcess(state.reason)
        }
      }
    }
  }

  fun stop() {
    stateListenerJob.cancel()
    ProgressSuspenderTracker.getInstance().stopTracking(progressSuspender)
  }

  class ProgressToTaskListener(private val taskSuspender: TaskSuspender) : ProgressSuspenderTracker.SuspenderListener {
    override fun onPause(suspendedText: String) {
      taskSuspender.pause(suspendedText)
    }

    override fun onResume() {
      taskSuspender.resume()
    }
  }

  companion object {
    private val LOG = logger<TaskToProgressSuspenderSynchronizer>()
  }
}