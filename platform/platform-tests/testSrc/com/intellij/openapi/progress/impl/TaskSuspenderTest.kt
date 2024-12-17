// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.CoroutineSuspender
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderElement
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

    val taskSuspender = TaskSuspender.suspendable("Paused by test")

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

    val outerSuspender = TaskSuspender.suspendable("Paused by test")
    val innerSuspender = TaskSuspender.suspendable("Paused by test")

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

  fun testTaskUnderCoroutineSuspenderIsSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val coroutineSuspender = coroutineSuspender(active = true)
    val task = launch(Dispatchers.Default + coroutineSuspender.asContextElement()) {
      startBackgroundTask(taskSuspender = null) {
        workUntilStopped(mayStop)
      }
    }

    suspendTaskAndRun(coroutineSuspender) {
      mayStop.complete(Unit)
      letBackgroundThreadsSuspend()

      assertFalse(task.isCompleted)
    }

    task.waitAssertCompletedNormally()
  }

  fun testTaskUnderOuterTaskSuspenderIsSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    // TaskSuspenderElement(taskSuspender) is used here instead taskSuspender.asContextElement(),
    // because it provides context with both task and coroutine suspenders.
    // Check that task is going to be suspended even if no `CoroutineScope` was in context beforehand
    val task = launch(Dispatchers.Default + TaskSuspenderElement(taskSuspender)) {
      startBackgroundTask(taskSuspender = null) {
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

  fun testInitialStateOfProgressSuspender(): Unit = timeoutRunBlocking {
    val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
    val connection = application.messageBus.connect()
    connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        progressSuspenderDeferred.complete(suspender)
      }
    })

    val mayStop = CompletableDeferred<Unit>().apply { complete(Unit) }

    val taskSuspender = TaskSuspender.suspendable("Paused by test").apply { pause() }
    val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderDeferred.await()
    assertTrue(progressSuspender.isSuspended)
    assertFalse(task.isCompleted)

    taskSuspender.resume()
    task.waitAssertCompletedNormally()
  }

  fun testTaskSuspendedByProgressSuspender(): Unit = timeoutRunBlocking {
    repeat(10) {
      val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
      val connection = application.messageBus.connect()
      connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
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

      connection.disconnect()
    }
  }

  fun testPauseResumeProgressSuspender(): Unit = timeoutRunBlocking {
    repeat(10) {
      val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
      val connection = application.messageBus.connect()
      connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
        override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
          progressSuspenderDeferred.complete(suspender)
        }
      })

      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderDeferred.await()

      progressSuspender.suspendProcess("Paused by ProgressSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      progressSuspender.resumeProcess()

      // Check that we don't start infinite updating loop
      repeat(10) {
        assertFalse(progressSuspender.isSuspended)
        assertFalse(taskSuspender.isPaused())
        letBackgroundThreadsSuspend()
      }

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()

      connection.disconnect()
    }
  }

  fun testPauseResumeTaskSuspender(): Unit = timeoutRunBlocking {
    repeat(10) {
      val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
      val connection = application.messageBus.connect()
      connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
        override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
          progressSuspenderDeferred.complete(suspender)
        }
      })

      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderDeferred.await()

      taskSuspender.pause("Paused by TaskSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      taskSuspender.resume()

      // Check that we don't start infinite updating loop
      repeat(10) {
        assertFalse(progressSuspender.isSuspended)
        assertFalse(taskSuspender.isPaused())
        letBackgroundThreadsSuspend()
      }

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()

      connection.disconnect()
    }
  }

  fun testProgressSuspenderSuspendedByTaskSuspender(): Unit = timeoutRunBlocking {
    val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
    application.messageBus.connect().subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        progressSuspenderDeferred.complete(suspender)
      }
    })

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderDeferred.await()
    assertFalse(progressSuspender.isSuspended)

    suspendTaskAndRun(taskSuspender) {
      assertTrue(progressSuspender.isSuspended)
      assertFalse(task.isCompleted)
    }

    letBackgroundThreadsSuspend()
    assertFalse(progressSuspender.isSuspended)
    mayStop.complete(Unit)
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

  private suspend fun suspendTaskAndRun(coroutineSuspender: CoroutineSuspender, action: suspend () -> Unit) {
    coroutineSuspender.pause()

    try {
      letBackgroundThreadsSuspend()
      action()
    }
    finally {
      coroutineSuspender.resume()
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