// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.CoroutineSuspender
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderElement
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

open class TaskSuspenderTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("rhizome.progress").setValue(true)
  }

  override fun tearDown() {
    Registry.get("rhizome.progress").resetToDefault()
    super.tearDown()
  }

  fun testSuspendResumeTask(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

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

    val task = startBackgroundTask(project, taskSuspender) {
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

    val task = startBackgroundTask(project, outerSuspender) {
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
    val task1 = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }
    val task2 = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

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
      startBackgroundTask(project, taskSuspender = null) {
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
      startBackgroundTask(project, taskSuspender = null) {
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
}


internal suspend fun suspendTaskAndRun(taskSuspender: TaskSuspender, action: suspend () -> Unit) {
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

internal suspend fun suspendTaskAndRun(coroutineSuspender: CoroutineSuspender, action: suspend () -> Unit) {
  coroutineSuspender.pause()

  try {
    letBackgroundThreadsSuspend()
    action()
  }
  finally {
    coroutineSuspender.resume()
  }
}

internal suspend fun workUntilStopped(mayStop: Deferred<Unit>) {
  while (true) {
    delay(1)
    checkCanceled()
    if (mayStop.isCompleted) {
      break
    }
  }
}

internal suspend fun CoroutineScope.startBackgroundTask(project: Project, taskSuspender: TaskSuspender?, action: suspend () -> Any): Job {
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

internal suspend fun letBackgroundThreadsSuspend(): Unit = delay(30.milliseconds)

internal suspend fun Job.waitAssertCompletedNormally() {
  join()
  assertFalse(isCancelled)
}