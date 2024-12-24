// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
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
    val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
    val connection = application.messageBus.connect()
    connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        progressSuspenderDeferred.complete(suspender)
      }
    })

    val mayStop = CompletableDeferred<Unit>().apply { complete(Unit) }

    val taskSuspender = TaskSuspender.suspendable("Paused by test").apply { pause() }
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

    val progressSuspender = progressSuspenderDeferred.await()
    assertTrue(progressSuspender.isSuspended)
    assertFalse(task.isCompleted)

    taskSuspender.resume()
    task.waitAssertCompletedNormally()
  }

  fun testTaskSuspendedByProgressSuspender(): Unit = timeoutRunBlocking {
    val progressSuspenderDeferred = CompletableDeferred<ProgressSuspender>()
    val connection = application.messageBus.connect()
    connection.subscribe(ProgressSuspender.TOPIC, object : ProgressSuspender.SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        progressSuspenderDeferred.complete(suspender)
      }
    })

    val mayStop = CompletableDeferred<Unit>()

    val taskSuspender = TaskSuspender.suspendable("Paused by test")
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

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

  fun testSuspendResumeProgressSuspender(): Unit = timeoutRunBlocking {
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
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderDeferred.await()

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

      connection.disconnect()
    }
  }

  fun testSuspendResumeTaskSuspender(): Unit = timeoutRunBlocking {
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
      val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

      val progressSuspender = progressSuspenderDeferred.await()

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
    val task = startBackgroundTask(project, taskSuspender) { workUntilStopped(mayStop) }

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
}

private suspend fun letBackgroundThreadsSuspend(): Unit = delay(30.milliseconds)