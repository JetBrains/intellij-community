// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.testFramework.ProjectRule
import com.intellij.util.SystemProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail


@RunWith(JUnit4::class)
class DumbServiceScanningListenerTest {
  private val ignoreHeadlessKey: String = "intellij.progress.task.ignoreHeadless"
  private val forceDumbQueueTaskKey: String = "idea.force.dumb.queue.tasks"
  private var prevIgnoreHeadlessVal: String? = null
  private var prevForceDumbQueueTaskVal: String? = null
  private lateinit var project: Project
  private val cs = CoroutineScope(Dispatchers.IO + Job())

  companion object {
    @ClassRule
    @JvmField
    val p = ProjectRule(runPostStartUpActivities = false, preloadServices = false)
  }

  @Before
  fun setUp() {
    prevIgnoreHeadlessVal = SystemProperties.setProperty(ignoreHeadlessKey, "true")
    prevForceDumbQueueTaskVal = SystemProperties.setProperty(forceDumbQueueTaskKey, "true")
    project = p.project
    project.service<DumbService>().waitForSmartMode()
  }

  @After
  fun tearDown() {
    cs.cancel("End of test")
    SystemProperties.setProperty(ignoreHeadlessKey, prevIgnoreHeadlessVal)
    SystemProperties.setProperty(forceDumbQueueTaskKey, prevForceDumbQueueTaskVal)
  }

  @Test
  fun `test dumb service suspends when listener started in state (false)`() {
    `test dumb service suspends when listener started in state`(false)
  }

  @Test
  fun `test dumb service suspends when listener started in state (true)`() {
    `test dumb service suspends when listener started in state`(true)
  }

  private fun `test dumb service suspends when listener started in state`(initialScanningRunState: Boolean) {
    val listener = DumbServiceScanningListener(project, cs)
    val tc = DumbServiceScanningListener.TestCompanion(listener)
    val scanningState = MutableStateFlow(initialScanningRunState)
    val isPaused = AtomicBoolean(false)
    val exception = AtomicReference<Throwable?>()
    val dumbTaskStarted = CountDownLatch(1)

    project.service<DumbService>().queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        try {
          dumbTaskStarted.countDown()
          while (cs.isActive) {
            val suspender = ProgressSuspender.getSuspender(indicator)
            isPaused.set(suspender.isSuspended)
          }
        }
        catch (t: Throwable) {
          exception.set(t)
        }
      }
    })

    dumbTaskStarted.awaitOrThrow(1, "Dumb task didn't start")

    waitOneSecondOrFail("Listener is not active yet. Should be resumed.") { !isPaused.get() }

    tc.subscribe(scanningState)

    assertEquals(initialScanningRunState, scanningState.value, "Sanity")
    waitOneSecondOrFail("Listener should respect initialScanningRunState after subscription (expected: $initialScanningRunState)") {
      isPaused.get() == initialScanningRunState
    }

    repeat(10) {
      scanningState.value = !scanningState.value
      waitOneSecondOrFail("Should respect current scanning state after change (expected: ${scanningState.value})") {
        isPaused.get() == scanningState.value
      }
    }

    assertNull(exception.get())
  }

  private fun waitOneSecondOrFail(message: String, condition: () -> Boolean) {
    repeat(1000 / 10) {
      if (condition()) return
      else Thread.sleep(10)
    }

    fail(message)
  }

  private fun CountDownLatch.awaitOrThrow(seconds: Long, message: String) {
    if (!await(seconds, TimeUnit.SECONDS)) {
      fail(message)
    }
  }
}