// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ComponentUtil.forceMarkAsShowing
import com.intellij.util.ref.GCUtil
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@TestApplication
class LaunchOnShowTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  private val container = JPanel().also {
    forceMarkAsShowing(it, true)
  }

  @Test
  fun `init in acceptable modality on already showing component`(): Unit = edtTest {
    var executed = false
    val job = container.initOnShow("test") {
      executed = true
    }
    assertFalse(awaitValue(false) { executed })

    yield()
    assertTrue(awaitValue(true) { executed })
    assertTrue(awaitValue(true) { job.isCompleted })
    assertNotReferenced(container, job)
  }

  @Test
  fun `launch once in acceptable modality on already showing component`(): Unit = edtTest {
    var executed = false
    val job = container.launchOnceOnShow("test") {
      executed = true
    }
    assertFalse(awaitValue(false) { executed })

    yield()
    assertTrue(awaitValue(true) { executed })
    assertTrue(awaitValue(true) { job.isCompleted })
    assertNotReferenced(container, job)
  }

  @Test
  fun `init is restarted if canceled before starting`(): Unit = edtTest {
    val component = JLabel()
    var counter = 0
    val job = component.initOnShow("test") {
      ++counter
    }

    withShowingChanged { container.add(component) }
    withShowingChanged { container.remove(component) }
    withShowingChanged { container.add(component) }
    withTimeout(15L.seconds) {
      job.join()
    }
    assertEquals(1, awaitValue(1) { counter })
    assertTrue(awaitValue(true) { job.isCompleted })
  }

  @Test
  fun `init is restarted if canceled before completing`(): Unit = edtTest {
    val component = JLabel()
    var counter = 0
    val job = component.initOnShow("test") {
      ++counter
      delay(1.seconds)
    }
    withShowingChanged { container.add(component) }
    delay(500.milliseconds)
    withShowingChanged { container.remove(component) }
    delay(10.milliseconds)
    withShowingChanged { container.add(component) }
    withTimeout(15L.seconds) {
      job.join()
    }
    assertEquals(2, awaitValue(2) { counter })
    assertTrue(awaitValue(true) { job.isCompleted })
  }

  @Test
  fun `launch once is restarted if canceled before starting`(): Unit = edtTest {
    val component = JLabel()
    var counter = 0
    val job = component.launchOnceOnShow("test") {
      ++counter
    }

    withShowingChanged { container.add(component) }
    withShowingChanged { container.remove(component) }
    withShowingChanged { container.add(component) }
    withTimeout(15L.seconds) {
      job.join()
    }
    assertEquals(1, awaitValue(1) { counter })
    assertTrue(awaitValue(true) { job.isCompleted })
  }

  @Test
  fun `init in acceptable modality on fresh component`(): Unit = edtTest {
    val component = JLabel()
    var executed = false
    val job = component.initOnShow("test") {
      executed = true
    }

    yield()
    assertReferenced(component, job)
    assertFalse(awaitValue(false) { executed })
    assertFalse(awaitValue(false) { job.isCompleted })

    withShowingChanged {
      container.add(component)
    }
    yield()
    assertTrue(awaitValue(true) { executed })
    assertTrue(awaitValue(true) { job.isCompleted })

    assertNotReferenced(component, job)
  }

  @Test
  fun `launch once in acceptable modality on fresh component`(): Unit = edtTest {
    val component = JLabel()
    var executed = false
    val job = component.launchOnceOnShow("test") {
      executed = true
      awaitCancellation()
    }

    yield()
    assertReferenced(component, job)
    assertFalse(awaitValue(false) { executed })
    assertFalse(awaitValue(false) { job.isCompleted })

    withShowingChanged {
      container.add(component)
    }
    yield()
    assertTrue(awaitValue(true) { executed })
    assertFalse(awaitValue(false) { job.isCompleted })

    withShowingChanged {
      container.remove(component)
    }
    assertTrue(awaitValue(true) { executed })
    yield()
    assertTrue(awaitValue(true) { job.isCompleted })
    assertNotReferenced(component, job)
  }

  @Test
  fun `init is delayed until acceptable modality`(): Unit = edtTest {
    var executed = false
    withModalProgress(ModalTaskOwner.guess(), "", TaskCancellation.cancellable()) {
      container.initOnShow("test") {
        executed = true
      }
      // This is not a bug: we actually wait for "true" here, and expect it to time out and return "false".
      assertFalse(awaitValue(true) { executed })
    }
    yield()
    assertTrue(awaitValue(true) { executed })
  }

  @Test
  fun `launch once is delayed until acceptable modality`(): Unit = edtTest {
    var executed = false
    withModalProgress(ModalTaskOwner.guess(), "", TaskCancellation.cancellable()) {
      container.launchOnceOnShow("test") {
        executed = true
      }
      // This is not a bug: we actually wait for "true" here, and expect it to time out and return "false".
      assertFalse(awaitValue(true) { executed })
    }
    yield()
    assertTrue(awaitValue(true) { executed })
  }

  @Test
  fun `launch works in a long-running unconfined EDT coroutine`(): Unit = edtTest {
    var executed = false
    withContext(Dispatchers.Unconfined) {
      container.launchOnShow("test") {
        executed = true
      }
      var eventuallyExecuted = false
      // not using awaitValue because it's important to not let go of this unconfined thing,
      // and therefore we must not suspend the coroutine
      repeat(10) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        eventuallyExecuted = executed
      }
      assertTrue(eventuallyExecuted)
    }
  }

  @Test
  fun `init without adding it to hierarchy is GCed with component`(): Unit = edtTest {
    var component: Component? = JLabel()
    val jobRef = WeakReference(
      component!!.initOnShow("test") {
        awaitCancellation()
      }
    )
    assertReferenced(component, assertNotNull(jobRef.get()))
    component = null // forget it
    while (jobRef.get() != null) {
      yield()
      GCUtil.tryGcSoftlyReachableObjects()
    }
  }

  @Test
  fun `launch once without adding it to hierarchy is GCed with component`(): Unit = edtTest {
    var component: Component? = JLabel()
    val jobRef = WeakReference(
      component!!.launchOnceOnShow("test") {
        awaitCancellation()
      }
    )
    assertReferenced(component, assertNotNull(jobRef.get()))
    component = null // forget it
    while (jobRef.get() != null) {
      yield()
      GCUtil.tryGcSoftlyReachableObjects()
    }
  }

  @Test
  fun launch(): Unit = edtTest {
    val component = JLabel()
    var counter = 0
    val job = component.launchOnShow("") {
      counter++
    }
    assertEquals(0, awaitValue(0) { counter })
    yield()
    assertEquals(0, awaitValue(0) { counter })

    withShowingChanged { container.add(component) }
    yield()
    assertEquals(1, awaitValue(1) { counter })
    withShowingChanged { container.remove(component) }
    yield()
    assertEquals(1, awaitValue(1) { counter })

    withShowingChanged { container.add(component) }
    yield()
    assertEquals(2, awaitValue(2) { counter })
    withShowingChanged { container.remove(component) }
    yield()
    assertEquals(2, awaitValue(2) { counter })

    job.cancelAndJoin()
    withShowingChanged { container.add(component) }
    yield()
    assertEquals(2, awaitValue(2) { counter })

    assertNotReferenced(component, job)
  }

  @Test
  fun `launch without adding it to hierarchy is GCed with component`(): Unit = edtTest {
    var component: Component? = JLabel()
    val jobRef = WeakReference(
      component!!.launchOnShow("test") {
        awaitCancellation()
      }
    )
    assertReferenced(component, assertNotNull(jobRef.get()))
    component = null // forget it
    while (jobRef.get() != null) {
      yield()
      GCUtil.tryGcSoftlyReachableObjects()
    }
  }
  
  private suspend fun <T> awaitValue(expected: T, getter: () -> T): T {
    val mark = TimeSource.Monotonic.markNow()
    var value = getter()
    while (mark.elapsedNow() < 5.seconds) {
      if (value == expected) break
      delay(1.milliseconds)
      value = getter()
    }
    return value
  }

  private fun edtTest(block: suspend CoroutineScope.() -> Unit) {
    timeoutRunBlocking(timeout = 1.hours) {
      withForcedRespectIsShowingClientProperty {
        withContext(Dispatchers.EDT, block)
      }
    }
  }
}
