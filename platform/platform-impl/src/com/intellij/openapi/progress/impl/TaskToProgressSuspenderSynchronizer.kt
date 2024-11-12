// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderListener

internal class TaskToProgressSuspenderSynchronizer(
  taskSuspender: TaskSuspender,
  private val progressSuspender: ProgressSuspender,
) {

  init {
    ProgressSuspenderTracker.getInstance().startTracking(progressSuspender, ProgressToTaskListener(taskSuspender))
    taskSuspender.addListener(TaskToProgressListener(progressSuspender))
  }

  fun stop() {
    ProgressSuspenderTracker.getInstance().stopTracking(progressSuspender)
  }

  class TaskToProgressListener(private val progressSuspender: ProgressSuspender) : TaskSuspenderListener {
    override fun onPause(reason: String?) {
      progressSuspender.suspendProcess(reason)
    }

    override fun onResume() {
      progressSuspender.resumeProcess()
    }
  }

  class ProgressToTaskListener(private val taskSuspender: TaskSuspender) : ProgressSuspenderTracker.SuspenderListener {
    override fun onPause(suspendedText: String) {
      taskSuspender.pause(suspendedText)
    }

    override fun onResume() {
      taskSuspender.resume()
    }
  }
}