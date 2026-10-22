// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Exercises [DefaultCompensatablePool.waitForStatus] with a [FakeClock]:
 * a large oversleep skips the check, and a small oversleep extends the unresponsiveness threshold.
 */
internal class DefaultCompensatablePoolTest {
  private val unresponsiveIntervalMs = 2000L

  private fun newPool(clock: FakeClock, lastSampleNs: Long = 0): DefaultCompensatablePool {
    return DefaultCompensatablePool(
      dispatcher = Dispatchers.Default,
      getLastSampleNs = { lastSampleNs },
      unresponsiveIntervalMs = unresponsiveIntervalMs,
      clock = clock,
    )
  }

  @Test
  fun `a large oversleep skips the check and waits again`() {
    // The first wait oversleeps by 5 s and the health check runs after the stall: the second wait decides.
    val clock = FakeClock(5000)
    val pool = newPool(clock, lastSampleNs = TimeUnit.MILLISECONDS.toNanos(6000))

    assertTrue(pool.waitForStatus(1000), "expected a fresh sample after the stall to keep the pool responsive")
    assertEquals(TimeUnit.MILLISECONDS.toNanos(7000), clock.nowNs, "expected a second wait after the large oversleep")
  }

  @Test
  fun `a stale sample after a large oversleep reports the pool stalled`() {
    // The first wait oversleeps by 5 s and is skipped. The second wait is on time, and the sample stays at time zero.
    val clock = FakeClock(5000)
    val pool = newPool(clock)

    assertFalse(pool.waitForStatus(1000), "expected a stale sample after the stall to report the pool stalled")
  }

  @Test
  fun `a small oversleep extends the unresponsiveness threshold`() {
    // The sample is 2.4 s old after the wait. This is above the interval, but below the interval plus the 500 ms oversleep.
    val clock = FakeClock(500)
    clock.advanceMs(900)
    val pool = newPool(clock)

    assertTrue(pool.waitForStatus(1000), "expected the oversleep to extend the unresponsiveness threshold")
    assertEquals(TimeUnit.MILLISECONDS.toNanos(2400), clock.nowNs, "expected one wait for a small oversleep")
  }

  @Test
  fun `a stale sample without oversleep reports the pool stalled`() {
    // The waits wake up on time while the sample stays at time zero: the pool itself is unresponsive.
    val clock = FakeClock()
    val pool = newPool(clock)

    assertTrue(pool.waitForStatus(1000), "expected the pool to stay responsive within the interval")
    assertFalse(pool.waitForStatus(1000), "expected a stale sample past the interval to report the pool stalled")
  }
}
