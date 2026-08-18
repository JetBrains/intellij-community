// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * End-to-end check that [ParallelismCompensator] actually relieves starvation: a `softLimitedParallelism(1)`
 * pool is flooded with blocking tasks (far more than its nominal capacity), and the observed concurrency is
 * expected to rise above 1 as the compensator grants extra parallelism to the stuck dispatcher.
 *
 * The compensator relies on a "heartbeat" -- something periodically updating [DefaultCompensatablePool.getLastSampleNs]
 * from a coroutine running on the watched dispatcher, exactly like [CoroutineDispatcherWatcher] does in production --
 * so this test runs that heartbeat itself instead of going through [CoroutineDispatcherWatcher].
 *
 * Sampling/compensation intervals are shrunk from their production defaults (seconds) to milliseconds so the
 * escalation happens, and the test finishes, quickly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ParallelismCompensatorStarvationTest {

  @Test
  fun `flooding a single-threaded pool with blocking tasks makes the compensator grant extra parallelism`() {
    val dispatcher = Dispatchers.Default
    val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()
    val scope = CoroutineScope(SupervisorJob())

    val heartbeat = object {
      @Volatile
      var lastSampleNs = System.nanoTime()
    }

    val heartbeatIntervalMs = 15L
    val heartbeatJob = scope.launch(CoroutineName("heartbeat") + dispatcher) {
      while (true) {
        delay(heartbeatIntervalMs.milliseconds)
        heartbeat.lastSampleNs = System.nanoTime()
      }
    }

    val compensator = ParallelismCompensator(
      pool = DefaultCompensatablePool(
        dispatcher = dispatcher,
        getLastSampleNs = { heartbeat.lastSampleNs },
        unresponsiveIntervalMs = 30,
      ),
      maxGrantsAllowed = 100,
      baseChecksForRevoke = 5,
      minConsecutiveChecksToConfirmRevoke = 3,
      samplingIntervalMs = 15,
    )

    try {
      compensator.start()

      val taskCount = 40
      val taskDurationMs = 300L
      val runningNow = AtomicInteger(0)
      val maxConcurrencySeen = AtomicInteger(0)
      val completed = CountDownLatch(taskCount)

      repeat(taskCount) {
        scope.launch(dispatcher) {
          val current = runningNow.incrementAndGet()
          maxConcurrencySeen.updateAndGet { previous -> maxOf(previous, current) }
          try {
            Thread.sleep(taskDurationMs)
          }
          finally {
            runningNow.decrementAndGet()
            completed.countDown()
          }
        }
      }

      // Sequentially running this test would be taking taskCount * taskDurationMs.
      // This timeout is much shorter -- the point isn't for every task to finish,
      // it's to give the compensator enough time to prove it granted extra parallelism.
      assertTrue(
        completed.await(5, TimeUnit.SECONDS),
        "Expected that all tasks eventually finish"
      )

      assertTrue(
        maxConcurrencySeen.get() > AVAILABLE_PROCESSORS,
        "Expected the compensator to grant extra parallelism to the starved pool, " +
        "but max observed concurrency was ${maxConcurrencySeen.get()}, expected larger than $AVAILABLE_PROCESSORS",
      )
    }
    finally {
      compensator.shutdown()
      heartbeatJob.cancel()
      scope.cancel()
    }
  }
}
