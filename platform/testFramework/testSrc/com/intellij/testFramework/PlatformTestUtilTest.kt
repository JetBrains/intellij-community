// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/** Verifies bounded event dispatch used by synchronous test-framework waits. */
@TestApplication
internal class PlatformTestUtilTest {

  /** Ensures an event that occupies the remaining budget cannot hide the deadline from the following event. */
  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  @Timeout(30)
  fun `deadline interrupts event queue draining between events`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val firstEventDispatched = AtomicBoolean()
      val secondEventDispatched = AtomicBoolean()
      val deadlineNs = System.nanoTime() + MILLISECONDS.toNanos(100)

      SwingUtilities.invokeLater {
        firstEventDispatched.set(true)
        //stall EDT until deadline is elapsed
        while (System.nanoTime() < deadlineNs + 1000) {
          Thread.onSpinWait()
        }
      }

      SwingUtilities.invokeLater {
        secondEventDispatched.set(true)
      }

      try {
        val allEventsDispatchedBeforeDeadline = PlatformTestUtil.dispatchAllEventsInIdeEventQueue(deadlineNs)

        //The first even must start executing, and basically stall the EDT until deadline is elapsed.
        // Hence, dispatchAllEventsInIdeEventQueue(deadline) must expire deadline

        assertTrue(
          firstEventDispatched.get(),
          "The first event must start executing"
        )
        assertFalse(
          allEventsDispatchedBeforeDeadline,
          "Deadline must expire before 2nd event is dispatched"
        )
        assertFalse(
          secondEventDispatched.get(),
          "Deadline must expire before 2nd event is dispatched"
        )
      }
      finally {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
    }
  }
}
