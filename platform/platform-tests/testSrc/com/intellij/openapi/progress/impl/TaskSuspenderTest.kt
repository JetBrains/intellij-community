// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.CoroutineSuspender
import com.intellij.openapi.progress.CoroutineSuspenderImpl
import com.intellij.openapi.progress.CoroutineSuspenderState
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.suspender.TaskSuspenderElement
import com.intellij.platform.ide.progress.suspender.TaskSuspenderImpl
import com.intellij.platform.ide.progress.suspender.TaskSuspenderState
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

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
      taskSuspender.waitForTasksToSuspend()
      mayStop.complete(Unit)
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
      taskSuspender.waitForTasksToSuspend()
      mayStop.complete(Unit)
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
      task.waitAssertCompletedNormally()
    }
  }

  fun testTasksWithTheSameSuspenderAreSuspended(): Unit = timeoutRunBlocking {
    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task1 = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }
    val task2 = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    suspendTaskAndRun(taskSuspender) {
      taskSuspender.waitForTasksToSuspend(tasksCount = 2)
      mayStop.complete(Unit)
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
      coroutineSuspender.waitForTasksToSuspend()
      mayStop.complete(Unit)
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
      taskSuspender.waitForTasksToSuspend()
      mayStop.complete(Unit)
    }

    task.waitAssertCompletedNormally()
  }
}


internal suspend fun suspendTaskAndRun(taskSuspender: TaskSuspender, action: suspend () -> Unit) {
  taskSuspender.pause(reason = "Paused by test")
  taskSuspender.state.first { it is TaskSuspenderState.Paused }

  try {
    action()
  }
  finally {
    taskSuspender.resume()
    taskSuspender.state.first { it == TaskSuspenderState.Active }
  }
}

internal suspend fun suspendTaskAndRun(coroutineSuspender: CoroutineSuspender, action: suspend () -> Unit) {
  coroutineSuspender.pause()

  try {
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

internal suspend fun TaskSuspender.waitForTasksToSuspend(tasksCount: Int = 1) {
  (this as TaskSuspenderImpl).coroutineSuspender.waitForTasksToSuspend(tasksCount)
}

internal suspend fun CoroutineSuspender.waitForTasksToSuspend(tasksCount: Int = 1) {
  val suspender = this as CoroutineSuspenderImpl
  suspender.state.first { it is CoroutineSuspenderState.Paused && it.continuations.size == tasksCount }
}

internal suspend fun Job.waitAssertCompletedNormally() {
  join()
  assertFalse(isCancelled)
}