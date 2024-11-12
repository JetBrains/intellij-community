// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderImpl
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.application
import kotlinx.coroutines.*

class TaskSuspenderTest : BasePlatformTestCase() {

  fun testSuspendResumeTask(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

    suspendTaskAndRun(taskSuspender) {
      mayStop.complete(Unit)
      letBackgroundThreadsSuspend()

      assertFalse(task.isCompleted)
    }

    task.waitAssertCompletedNormally()
  }

  fun testInnerTaskIsSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test") as TaskSuspenderImpl

    val task = startBackgroundTask(taskSuspender) {
      withBackgroundProgress(project, "TaskSuspenderTest inner task") {
        workUntilStopped(mayStop)
      }
    }

    suspendTaskAndRun(taskSuspender) {
      mayStop.complete(Unit)
      letBackgroundThreadsSuspend()

      assertFalse(task.isCompleted)
    }

    task.waitAssertCompletedNormally()
  }

  fun testInnerTaskWithCustomSuspenderIsNotSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val outerSuspender = TaskSuspender.suspendable("Paused by test") as TaskSuspenderImpl
    val innerSuspender = TaskSuspender.suspendable("Paused by test") as TaskSuspenderImpl

    val task = startBackgroundTask(outerSuspender) {
      withBackgroundProgress(project, "TaskSuspenderTest inner task", innerSuspender) {
        workUntilStopped(mayStop)
      }
    }

    suspendTaskAndRun(outerSuspender) {
      mayStop.complete(Unit)
      letBackgroundThreadsSuspend()

      assertTrue(task.isCompleted)
    }
  }

  fun testTasksWithTheSameSuspenderAreSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task1 = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }
    val task2 = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

    suspendTaskAndRun(taskSuspender) {
      mayStop.complete(Unit)
      letBackgroundThreadsSuspend()

      assertFalse(task1.isCompleted)
      assertFalse(task2.isCompleted)
    }

    task1.waitAssertCompletedNormally()
    task2.waitAssertCompletedNormally()
  }

  fun testTaskSuspendedByProgressSuspender(): Unit = timeoutRunBlocking {
    var progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
    application.messageBus.connect().subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        progressSuspenderDeferred.complete(suspender)
      }
    })

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderDeferred.await()

    progressSuspender.suspendProcess("Paused by ProgressSuspender")
    assertTrue(taskSuspender.isPaused())
    letBackgroundThreadsSuspend()

    mayStop.complete(Unit)
    letBackgroundThreadsSuspend()

    assertFalse(task.isCompleted)

    progressSuspender.resumeProcess()
    assertFalse(taskSuspender.isPaused())

    task.waitAssertCompletedNormally()
  }

  private suspend fun suspendTaskAndRun(taskSuspender: TaskSuspender, action: suspend () -> Unit) {
    taskSuspender.pause(reason = "Paused by test")
    assertTrue(taskSuspender.isPaused())

    try {
      letBackgroundThreadsSuspend()
      action()
    }
    finally {
      taskSuspender.resume()
      assertFalse(taskSuspender.isPaused())
    }
  }

  private suspend fun workUntilStopped(mayStop: Deferred<Unit>) {
    while (true) {
      delay(1)
      checkCanceled()
      if (mayStop.isCompleted) {
        break
      }
    }
  }

  private suspend fun CoroutineScope.startBackgroundTask(taskSuspender: TaskSuspender?, action: suspend () -> Any): Job {
    val taskStarted = CompletableDeferred<Unit>()
    val job = launch {
      val cancellation = TaskCancellation.nonCancellable()
      withBackgroundProgress(project, "TaskSuspenderTest task", cancellation, taskSuspender) {
        taskStarted.complete(Unit)
        action()
      }
    }

    taskStarted.await()
    return job
  }

  private suspend fun letBackgroundThreadsSuspend(): Unit = delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)

  private suspend fun Job.waitAssertCompletedNormally() {
    join()
    assertFalse(isCancelled)
  }
}