// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
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

  /**
   * A drained runnable queues a write-intent runnable after the canary has run, while a background write action starts.
   * The flush queue holds that runnable back and posts no event for it, so the drain must wait for the write action.
   */
  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  @Timeout(30)
  fun `drain waits for a write-intent runnable held back by a background write action`(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()
    val heldBackRunnableExecuted = AtomicBoolean()
    withContext(Dispatchers.EDT) {
      application.invokeLater {
        // both runnables below are queued behind the canary of the drain, so the canary runs before the write action is pending
        application.invokeLater {
          launch(Dispatchers.Default) {
            backgroundWriteAction {
              // keep the write action in progress while the drain reaches its stop condition
              TimeoutUtil.sleep(100)
            }
          }
          val deadlineNs = System.nanoTime() + SECONDS.toNanos(10)
          while (!application.threadingSupport.isWriteActionPending()) {
            check(System.nanoTime() < deadlineNs) { "The background write action did not become pending" }
            Thread.onSpinWait()
          }
        }
        application.invokeLater {
          heldBackRunnableExecuted.set(true)
        }
      }

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertTrue(
        heldBackRunnableExecuted.get(),
        "The drain returned while the flush queue held a write-intent runnable back"
      )
    }
  }
}
