// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
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
    assertTrue(taskSuspender.isPaused())
    letBackgroundThreadsSuspend()

    mayStop.complete(Unit)
    letBackgroundThreadsSuspend()

    assertFalse(task.isCompleted)

    progressSuspender.resumeProcess()
    assertFalse(taskSuspender.isPaused())

    task.waitAssertCompletedNormally()
  }

  fun testSuspendResumeProgressSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, _) = subscribeOnProgressSuspender()

    repeat(10) {
      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderChannel.receive()

      progressSuspender.suspendProcess("Paused by ProgressSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      progressSuspender.resumeProcess()

      // Check that we don't start an infinite updating loop
      repeat(10) {
        assertFalse(progressSuspender.isSuspended)
        assertFalse(taskSuspender.isPaused())
        letBackgroundThreadsSuspend()
      }

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()
    }
  }

  fun testSuspendResumeTaskSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, _) = subscribeOnProgressSuspender()

    repeat(10) {
      val mayStop = CompletableDeferred<Unit>()

      val taskSuspender = TaskSuspender.suspendable("Paused by test")
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderChannel.receive()

      taskSuspender.pause("Paused by TaskSuspender")
      // resume immediately to check that there is no race between suspenders states synchronization
      taskSuspender.resume()

      // Check that we don't start an infinite updating loop
      repeat(10) {
        assertFalse(progressSuspender.isSuspended)
        assertFalse(taskSuspender.isPaused())
        letBackgroundThreadsSuspend()
      }

      mayStop.complete(Unit)
      task.waitAssertCompletedNormally()
    }
  }

  fun testProgressSuspenderSuspendedByTaskSuspender(): Unit = timeoutRunBlocking {
    val (progressSuspenderChannel, _) = subscribeOnProgressSuspender()

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderChannel.receive()
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
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun subscribeOnProgressSuspender() : Pair<Channel<ProgressSuspender>, Channel<Boolean>> {
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

private suspend fun letBackgroundThreadsSuspend(): Unit = delay(30.milliseconds)