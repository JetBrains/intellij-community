// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [ParallelismCompensator.parallelismCompensationLoop] against a fake [CompensatablePool], so the
 * algorithm can be driven synchronously, without real time passing or a real dispatcher.
 *
 * [ParallelismCompensator.parallelismCompensationLoop] never returns on its own -- in production it runs on a
 * dedicated thread for the process lifetime. Each fake below throws a sentinel exception from [CompensatablePool]
 * once it has seen enough calls to prove its point, which is how these tests escape the loop.
 */
internal class ParallelismCompensatorTest {

  private class StopTest : RuntimeException()

  private fun newCompensator(
    pool: CompensatablePool,
    maxGrantsAllowed: Int = 10,
    baseChecksForRevoke: Int = 3,
    minConsecutiveChecksToConfirmRevoke: Int = 2,
    samplingIntervalMs: Long = 5,
  ): ParallelismCompensator {
    return ParallelismCompensator(
      pool = pool,
      maxGrantsAllowed = maxGrantsAllowed,
      baseChecksForRevoke = baseChecksForRevoke,
      minConsecutiveChecksToConfirmRevoke = minConsecutiveChecksToConfirmRevoke,
      samplingIntervalMs = samplingIntervalMs,
    )
  }

  @Test
  fun `never grants extra parallelism while the pool is never starved`() {
    var grants = 0
    var calls = 0
    val fake = object : CompensatablePool {
      override fun waitForStatus(timeMs: Long): Boolean {
        calls++
        if (calls > 200) throw StopTest()
        return true
      }

      override fun withGrantedParallelism(block: () -> Unit) {
        grants++
        block()
      }
    }

    val compensator = newCompensator(fake)
    try {
      assertThrows(StopTest::class.java) { compensator.parallelismCompensationLoop() }
      assertEquals(0, grants, "a pool that is always responsive should never need extra parallelism")
    }
    finally {
      compensator.shutdown()
    }
  }

  @Test
  fun `permanent starvation grants extra parallelism with a growing wait before each new grant`() {
    var calls = 0
    var totalWaitMs = 0L
    var revoked = 0
    val waitMsAtGrant = mutableListOf<Long>()
    val fake = object : CompensatablePool {
      override fun waitForStatus(timeMs: Long): Boolean {
        calls++
        totalWaitMs += timeMs
        if (calls > 20) throw StopTest()
        return false
      }

      override fun withGrantedParallelism(block: () -> Unit) {
        waitMsAtGrant += totalWaitMs
        block()
        revoked++
      }
    }

    val compensator = newCompensator(fake)
    try {
      assertThrows(StopTest::class.java) { compensator.parallelismCompensationLoop() }
      assertEquals(0, revoked, "Starved pool only is revoked")
      assertTrue(waitMsAtGrant.size >= 3, "expected several grants before the call budget ran out: $waitMsAtGrant")
      val waitBetweenGrants = waitMsAtGrant.zipWithNext { previous, next -> next - previous }
      for (i in 1 until waitBetweenGrants.size) {
        assertTrue(
          waitBetweenGrants[i] >= waitBetweenGrants[i - 1] * 2,
          "expected the wait at least two times more before each new grant to grow, to avoid over-flooding the pool: $waitBetweenGrants",
        )
      }
    }
    finally {
      compensator.shutdown()
    }
  }

  @Test
  fun `permanent starvation never grants more than maxGrantsAllowed`() {
    val maxGrantsAllowed = 4
    var calls = 0
    var depth = 0
    var maxDepthSeen = 0
    val fake = object : CompensatablePool {
      override fun waitForStatus(timeMs: Long): Boolean {
        calls++
        if (calls > 1000) throw StopTest()
        return false
      }

      override fun withGrantedParallelism(block: () -> Unit) {
        depth++
        maxDepthSeen = maxOf(maxDepthSeen, depth)
        try {
          block()
        }
        finally {
          depth--
        }
      }
    }

    val compensator = newCompensator(fake, maxGrantsAllowed = maxGrantsAllowed)
    try {
      assertThrows(StopTest::class.java) { compensator.parallelismCompensationLoop() }
      assertEquals(maxGrantsAllowed, maxDepthSeen, "expected granted parallelism to reach but never exceed the configured maximum")
    }
    finally {
      compensator.shutdown()
    }
  }

  @Test
  fun `starvation that ends returns the pool to its normal parallelism`() {
    val recoversAfterCall = 30
    var calls = 0
    var depth = 0
    var depthAtStop = -1
    var grants = 0
    val fake = object : CompensatablePool {
      override fun waitForStatus(timeMs: Long): Boolean {
        calls++
        if (calls > 100) {
          depthAtStop = depth
          throw StopTest()
        }
        return calls > recoversAfterCall
      }

      override fun withGrantedParallelism(block: () -> Unit) {
        grants++
        depth++
        try {
          block()
        }
        finally {
          depth--
        }
      }
    }

    val compensator = newCompensator(fake)
    try {
      assertThrows(StopTest::class.java) { compensator.parallelismCompensationLoop() }

      assertTrue(grants > 0, "expected the starved pool to be granted extra parallelism at least once")
      assertEquals(0, depthAtStop, "expected every granted unit to be revoked once the pool recovered")
    }
    finally {
      compensator.shutdown()
    }
  }

  @Test
  fun `flapping starvation requires longer proof of recovery on each cycle`() {
    var calls = 0
    var depth = 0
    var timeAlive = 0L
    var timeStarved = 0L
    val starvationLimit = 2
    val fake = object : CompensatablePool {
      override fun waitForStatus(timeMs: Long): Boolean {
        calls++
        if (calls > 400) throw StopTest()
        // The pool is only alive while it currently holds extra parallelism -- taking it back immediately
        // starves it again, modelling a pool that flaps between starved and alive.
        val alive = depth > starvationLimit
        if (alive) {
          timeAlive += timeMs
        } else {
          timeStarved += timeMs
        }
        return alive
      }

      override fun withGrantedParallelism(block: () -> Unit) {
        depth++
        try {
          block()
        }
        finally {
          depth--
        }
      }
    }

    val compensator = newCompensator(fake, baseChecksForRevoke = 2, minConsecutiveChecksToConfirmRevoke = 1)
    try {
      assertThrows(StopTest::class.java) { compensator.parallelismCompensationLoop() }

      assertTrue(timeAlive > timeStarved * 20, "despite flapping most of the time pool should be alive")
    }
    finally {
      compensator.shutdown()
    }
  }
}
