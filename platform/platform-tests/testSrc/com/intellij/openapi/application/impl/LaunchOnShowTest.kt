// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ComponentUtil.forceMarkAsShowing
import com.intellij.util.ref.GCUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.launchOnceOnShow
import com.intellij.util.ui.withShowingChanged
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
import kotlin.time.Duration.Companion.seconds

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
  fun `launch once in acceptable modality on already showing component`(): Unit = edtTest {
    var executed = false
    val job = container.launchOnceOnShow("test") {
      executed = true
    }
    assertFalse(executed)

    yield()
    assertTrue(executed)
    assertTrue(job.isCompleted)
    assertNotReferenced(container, job)
  }

  @Test
  fun `launch once is restarted if canceled before starting`(): Unit = edtTest {
    val component = JLabel()
    var executed = false
    val job = component.launchOnceOnShow("test") {
      executed = true
    }

    withShowingChanged { container.add(component) }
    withShowingChanged { container.remove(component) }
    withShowingChanged { container.add(component) }
    withTimeout(15L.seconds) {
      job.join()
    }
    assertTrue(executed)
    assertTrue(job.isCompleted)
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
    assertFalse(executed)
    assertFalse(job.isCompleted)

    withShowingChanged {
      container.add(component)
    }
    yield()
    assertTrue(executed)
    assertFalse(job.isCompleted)

    withShowingChanged {
      container.remove(component)
    }
    assertTrue(executed)
    yield()
    assertTrue(job.isCompleted)
    assertNotReferenced(component, job)
  }

  @Test
  fun `launch once is delayed until acceptable modality`(): Unit = edtTest {
    var executed = false
    withModalProgress(ModalTaskOwner.guess(), "", TaskCancellation.cancellable()) {
      container.launchOnceOnShow("test") {
        executed = true
      }
      yield()
      assertFalse(executed)
    }
    yield()
    assertTrue(executed)
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
    assertEquals(0, counter)
    yield()
    assertEquals(0, counter)

    withShowingChanged { container.add(component) }
    yield()
    assertEquals(1, counter)
    withShowingChanged { container.remove(component) }
    yield()
    assertEquals(1, counter)

    withShowingChanged { container.add(component) }
    yield()
    assertEquals(2, counter)
    withShowingChanged { container.remove(component) }
    yield()
    assertEquals(2, counter)

    job.cancelAndJoin()
    withShowingChanged { container.add(component) }
    yield()
    assertEquals(2, counter) // still 2

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

  private fun edtTest(block: suspend CoroutineScope.() -> Unit) {
    timeoutRunBlocking(timeout = 1.hours) {
      withContext(Dispatchers.EDT, block)
    }
  }
}
