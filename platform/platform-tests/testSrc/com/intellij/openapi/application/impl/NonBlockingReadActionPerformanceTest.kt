// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readLockCompensationTimeout
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@StressTestApplication
@PerformanceUnitTest
class NonBlockingReadActionPerformanceTest {

  /**
   * Many coroutines on [Dispatchers.Default] run a long synchronous NBRA inside a read action.
   * The NBRA can always run here, so it must not ask the scheduler for extra workers.
   * Extra workers would run more CPU-bound computations than the dispatcher has CPU workers.
   */
  @Test
  fun `synchronous NBRA inside readAction does not oversubscribe Default dispatcher`() {
    val threadMXBean = ManagementFactory.getThreadMXBean()
    Assumptions.assumeTrue(threadMXBean.isCurrentThreadCpuTimeSupported, "Thread CPU time is not supported")
    Assumptions.assumeTrue(readLockCompensationTimeout != -1, "Parallelism compensation is disabled")

    val parallelism = defaultDispatcherParallelism()
    val coroutineCount = parallelism * COROUTINES_PER_WORKER
    // the computation must be longer than the compensation timeout of a synchronous NBRA
    val computationCpuTimeNs = TimeUnit.MILLISECONDS.toNanos(readLockCompensationTimeout * 2L + 250)
    // A fixed amount of work, not a fixed CPU time, so that cache misses and context switches add wall time.
    // The first calibration warms up the JIT.
    calibratePasses(computationCpuTimeNs)
    val passes = calibratePasses(computationCpuTimeNs)

    // The first use of a service creates it with parallelism compensation.
    // Create the services before the measurement, so that this compensation does not add workers.
    runBlockingMaybeCancellable {
      readAction {
        ReadAction.nonBlocking<Unit> {}.executeSynchronously()
      }
    }

    val running = AtomicInteger()
    val peak = AtomicInteger()

    Benchmark.newBenchmark("synchronous NBRA in many readAction coroutines") {
      running.set(0)
      peak.set(0)
      runBlockingMaybeCancellable {
        repeat(coroutineCount) {
          launch(Dispatchers.Default) {
            readAction {
              ReadAction.nonBlocking<Unit> {
                peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                try {
                  val workingSet = IntArray(WORKING_SET_INTS)
                  repeat(passes) {
                    touch(workingSet)
                    Cancellation.checkCancelled()
                  }
                  sink.addAndGet(workingSet[0].toLong())
                }
                finally {
                  running.decrementAndGet()
                }
              }.executeSynchronously()
            }
          }
        }
      }
      println("NBRA_PEAK peak=${peak.get()} parallelism=$parallelism passes=$passes")
    }
      .attempts(3)
      .start()
  }

  private val sink = AtomicLong()

  /**
   * Returns the number of [touch] passes over a working set that take [cpuTimeNs] of CPU time on the current thread.
   */
  private fun calibratePasses(cpuTimeNs: Long): Int {
    val threadMXBean = ManagementFactory.getThreadMXBean()
    val workingSet = IntArray(WORKING_SET_INTS)
    var passes = 0
    val start = threadMXBean.currentThreadCpuTime
    while (threadMXBean.currentThreadCpuTime - start < cpuTimeNs) {
      touch(workingSet)
      passes++
    }
    sink.addAndGet(workingSet[0].toLong())
    return passes
  }

  /**
   * Writes one int in each cache line of [workingSet].
   * Each computation has a private working set, so a thread that runs after a context switch evicts it from the CPU cache.
   */
  private fun touch(workingSet: IntArray) {
    for (i in workingSet.indices step CACHE_LINE_INTS) {
      workingSet[i]++
    }
  }

  /**
   * Returns the number of CPU workers of [Dispatchers.Default], computed as in `kotlinx.coroutines.scheduling.CORE_POOL_SIZE`.
   */
  private fun defaultDispatcherParallelism(): Int {
    return System.getProperty("kotlinx.coroutines.scheduler.core.pool.size")?.toIntOrNull()
           ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
  }
}

private const val COROUTINES_PER_WORKER = 8

// 1 MB for each computation
private const val WORKING_SET_INTS = 1 shl 18

private const val CACHE_LINE_INTS = 16
