// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.application
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests TaskSuspender against old implementation with ProgressIndicators (`rhizome.progress` flag is false)
 */
class TaskSuspenderTestWithProgressIndicator : TaskSuspenderTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("rhizome.progress").setValue(false)
  }

  override fun tearDown() {
    Registry.get("rhizome.progress").resetToDefault()
    super.tearDown()
  }

  fun testInitialStateOfProgressSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, _) = subscribeOnProgressSuspender()

    val mayStop = CompletableDeferred<Unit>().apply { complete(Unit) }

    val taskSuspender = TaskSuspender.suspendable("Paused by test").apply { pause() }
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderChannel.receive()
    assertTrue(progressSuspender.isSuspended)
    assertFalse(task.isCompleted)

    taskSuspender.resume()
    task.waitAssertCompletedNormally()
  }

  fun testTaskSuspendedByProgressSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, _) = subscribeOnProgressSuspender()

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderChannel.receive()

    progressSuspender.suspendProcess("Paused by ProgressSuspender")
    taskSuspender.waitForTasksToSuspend()

    mayStop.complete(Unit)
    progressSuspender.resumeProcess()
    task.waitAssertCompletedNormally()
  }

  fun testSuspendResumeProgressSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, progressSuspenderIsPaused) = subscribeOnProgressSuspender()

    repeat(10) {
      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderChannel.receive()

      var taskSuspenderUpdatesCount = 0
      val taskSuspenderJob = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        taskSuspender.state.collect { taskSuspenderUpdatesCount++ }
      }

      var progressSuspenderUpdatesCount = 0
      val progressSuspenderJob = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        progressSuspenderIsPaused.receiveAsFlow().collect { progressSuspenderUpdatesCount++ }
      }

      progressSuspender.suspendProcess("Paused by ProgressSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      progressSuspender.resumeProcess()

      // Let the threads run for a while to ensure that we don't start an infinite updating loop
      delay(200.milliseconds)

      assertFalse(progressSuspender.isSuspended)
      assertFalse(taskSuspender.isPaused())

      assertEquals("TaskSuspender should be updated 3 times (initial, pause, resume)", 3, taskSuspenderUpdatesCount)
      assertEquals("ProgressSuspender should be updated 2 times (pause, resume)", 2, progressSuspenderUpdatesCount)

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()

      taskSuspenderJob.cancel()
      progressSuspenderJob.cancel()
    }
  }

  fun testSuspendResumeTaskSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, progressSuspenderIsPaused) = subscribeOnProgressSuspender()

    repeat(10) {
      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderChannel.receive()

      var taskSuspenderUpdatesCount = 0
      val taskSuspenderJob = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        taskSuspender.state.collect { taskSuspenderUpdatesCount++ }
      }

      var progressSuspenderUpdatesCount = 0
      val progressSuspenderJob = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        progressSuspenderIsPaused.receiveAsFlow().collect { progressSuspenderUpdatesCount++ }
      }

      taskSuspender.pause("Paused by TaskSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      taskSuspender.resume()

      // Let the threads run for a while to ensure that we don't start an infinite updating loop
      delay(200.milliseconds)

      assertFalse(progressSuspender.isSuspended)
      assertFalse(taskSuspender.isPaused())

      assertEquals("TaskSuspender should be updated 3 times (initial, pause, resume)", 3, taskSuspenderUpdatesCount)
      assertEquals("ProgressSuspender should be updated 2 times (pause, resume)", 2, progressSuspenderUpdatesCount)

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()

      taskSuspenderJob.cancel()
      progressSuspenderJob.cancel()
    }
  }

  fun testProgressSuspenderSuspendedByTaskSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, progressSuspenderIsPaused) = subscribeOnProgressSuspender()

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderChannel.receive()
    assertFalse(progressSuspender.isSuspended)

    suspendTaskAndRun(taskSuspender) {
      taskSuspender.waitForTasksToSuspend()
      assertTrue("ProgressSuspender should be paused", progressSuspenderIsPaused.receive())
    }

    assertFalse("ProgressSuspender should be resumed", progressSuspenderIsPaused.receive())
    mayStop.complete(Unit)
    task.waitAssertCompletedNormally()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun subscribeOnProgressSuspender(): Pair<Channel<ProgressSuspender>, Channel<Boolean>> {
  val progressSuspender = Channel<ProgressSuspender>(capacity = UNLIMITED)
  val isPaused = Channel<Boolean>(capacity = UNLIMITED)

  application.messageBus.connect().subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
    override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
      progressSuspender.trySend(suspender)
    }

    override fun suspendedStatusChanged(suspender: ProgressSuspender) {
      isPaused.trySend(suspender.isSuspended)
    }
  })

  return progressSuspender to isPaused
}