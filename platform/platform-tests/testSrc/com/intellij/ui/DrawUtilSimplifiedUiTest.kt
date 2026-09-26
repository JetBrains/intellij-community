// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The platform calls a listener off the EDT, so these tests await an event instead of pumping the event queue.
 * A case that asserts the absence of an event ends with a known change, because the platform keeps one order.
 */
@TestApplication
internal class DrawUtilSimplifiedUiTest {

  @Test
  fun `subscribe calls the listener once`(): Unit = timeoutRunBlocking {
    val parent = Disposer.newDisposable()
    try {
      val seen = subscribe(parent)
      assertEquals(DrawUtil.isSimplifiedUI(), seen.receive())
    } finally {
      Disposer.dispose(parent)
    }
  }

  @Test
  fun `a source publishes only a real change`(): Unit = timeoutRunBlocking {
    assumeFalse(DrawUtil.isSimplifiedUI(), "the case drives the value up from false")
    val parent = Disposer.newDisposable()
    try {
      val seen = subscribe(parent)
      assertEquals(false, seen.receive())

      PowerSaveMode.setEnabled(true) // false -> true
      assertEquals(true, seen.receive())

      Registry.get("ui.simplified").setValue(true) // already true, so no change
      PowerSaveMode.setEnabled(false) // the registry key holds it true, so no change

      Registry.get("ui.simplified").setValue(false) // true -> false
      assertEquals(false, seen.receive())

      assertNull(seen.tryReceive().getOrNull(), "a source that does not move the value must publish nothing")
    } finally {
      Disposer.dispose(parent)
      Registry.get("ui.simplified").resetToDefault()
      PowerSaveMode.setEnabled(false)
    }
  }

  @Test
  fun `a source call without a value change publishes nothing`(): Unit = timeoutRunBlocking {
    assumeFalse(DrawUtil.isSimplifiedUI(), "the case needs a value that the remote session cannot move")
    val parent = Disposer.newDisposable()
    try {
      val seen = subscribe(parent)
      assertEquals(false, seen.receive())

      // isRemoteSession() cannot move on a host that runs no remote session
      RemoteDesktopService.fireRemoteSessionChanged()
      // the call above hops through the event queue, so let it reach the tracker first
      withContext(Dispatchers.EDT) { UIUtil.dispatchAllInvocationEvents() }

      PowerSaveMode.setEnabled(true) // a real change, so the tracker handles it after the hop
      assertEquals(true, seen.receive())

      assertNull(seen.tryReceive().getOrNull())
    } finally {
      Disposer.dispose(parent)
      PowerSaveMode.setEnabled(false)
    }
  }

  @Test
  fun `disposing the parent ends the subscription`(): Unit = timeoutRunBlocking {
    assumeFalse(DrawUtil.isSimplifiedUI(), "the case drives the value up from false")
    val parent = Disposer.newDisposable()
    val other = Disposer.newDisposable()
    try {
      val seen = subscribe(parent)
      val seenByOther = subscribe(other)
      assertEquals(false, seen.receive())
      assertEquals(false, seenByOther.receive())

      Disposer.dispose(parent)
      PowerSaveMode.setEnabled(true)

      // one publish reaches every listener, so the live one proves the publish happened
      assertEquals(true, seenByOther.receive())
      assertNull(seen.tryReceive().getOrNull())
    } finally {
      Disposer.dispose(other)
      PowerSaveMode.setEnabled(false)
    }
  }

  /**
   * The platform promises that it serializes the calls, so a listener can cache the value without a lock.
   * The sleep widens the window that an unordered dispatcher opens.
   */
  @Test
  fun `the platform never overlaps two calls to one listener`(): Unit = timeoutRunBlocking {
    assumeFalse(DrawUtil.isSimplifiedUI(), "the case drives the value up from false")
    val parent = Disposer.newDisposable()
    val running = AtomicBoolean()
    val overlapped = AtomicBoolean()
    val seen = Channel<Boolean>(Channel.UNLIMITED)
    try {
      DrawUtil.subscribeSimplifiedUI(parent) {
        if (!running.compareAndSet(false, true)) {
          overlapped.set(true)
        }
        Thread.sleep(2)
        running.set(false)
        seen.trySend(DrawUtil.isSimplifiedUI())
      }

      repeat(50) {
        PowerSaveMode.setEnabled(true)
        PowerSaveMode.setEnabled(false)
      }
      PowerSaveMode.setEnabled(true)
      while (!seen.receive()) {
        // wait until a call reads the value the churn ends on
      }

      assertFalse(overlapped.get(), "two calls to one listener ran at the same time")
    } finally {
      Disposer.dispose(parent)
      PowerSaveMode.setEnabled(false)
    }
  }

  /** @return what the listener read on each call, in order */
  private fun subscribe(parent: Disposable): Channel<Boolean> {
    val seen = Channel<Boolean>(Channel.UNLIMITED)
    DrawUtil.subscribeSimplifiedUI(parent) {
      seen.trySend(DrawUtil.isSimplifiedUI())
    }
    return seen
  }
}
