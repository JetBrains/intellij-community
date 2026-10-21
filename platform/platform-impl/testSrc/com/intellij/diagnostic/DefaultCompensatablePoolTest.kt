// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [DefaultCompensatablePool.waitForStatus] with a [FakeClock]:
 * a process stall keeps the pool responsive, so the compensator grants no parallelism for it.
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
  fun `a process stall during the wait keeps the pool responsive`() {
    // The wait oversleeps by 5 s and the sample is stale by the same stall: no grant.
    val clock = FakeClock(5000)
    val pool = newPool(clock)

    assertTrue(pool.waitForStatus(1000), "expected a stalled process to keep the pool responsive")
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
