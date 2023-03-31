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
import kotlin.test.assertFalse
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
  fun `test dumb service suspends when listener started in different initial scanning states (false)`() {
    `test dumb service suspends when listener started in different initial scanning states`(false)
  }

  @Test
  fun `test dumb service suspends when listener started in different initial scanning states (true)`() {
    `test dumb service suspends when listener started in different initial scanning states`(true)
  }

  private fun `test dumb service suspends when listener started in different initial scanning states`(initialScanningRunState: Boolean) {
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

    Thread.sleep(10) // this is for our test to update isPaused in background thread
    assertFalse(isPaused.get(), "Listener is not active yet. Should be resumed.")

    tc.subscribe(scanningState)

    Thread.sleep(10) // this is for our test to update isPaused in background thread
    assertEquals(initialScanningRunState, scanningState.value, "Sanity")
    assertEquals(initialScanningRunState, isPaused.get(), "Listener should respect initialScanningRunState after subscription")

    repeat(10) {
      scanningState.value = !scanningState.value
      Thread.sleep(10) // this is for our test to update isPaused in background thread
      assertEquals(scanningState.value, isPaused.get(), "Should respect current scanning state after change")
    }

    assertNull(exception.get())
  }

  private fun CountDownLatch.awaitOrThrow(seconds: Long, message: String) {
    if (!await(seconds, TimeUnit.SECONDS)) {
      fail(message)
    }
  }
}