// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [PooledThreadDumper.dumpThreadsIfNeeded] with a [FakeClock]:
 * a process stall saves no dump, a real pool exhaustion saves one and reports the measured time.
 */
internal class PooledThreadDumperTest {
  private val thresholdMs = 2000

  private fun newDumper(clock: FakeClock, warnings: MutableList<String>, dumps: MutableList<Unit>): PooledThreadDumper {
    return PooledThreadDumper(
      dispatcher = Dispatchers.Default,
      getUnresponsiveIntervalMs = { thresholdMs },
      clock = clock,
      maxDumps = { 10 },
      dumpThreads = { dumps.add(Unit); null },
      warn = warnings::add,
    )
  }

  @Test
  fun `a process stall saves no dump and writes no warning`() {
    val clock = FakeClock()
    val warnings = mutableListOf<String>()
    val dumps = mutableListOf<Unit>()
    val dumper = newDumper(clock, warnings, dumps)

    // The process stalled for 6 s: the sampler is stale and the watcher overslept by the same time.
    clock.advanceMs(6000)
    dumper.dumpThreadsIfNeeded(lastSampleNs = 0, oversleptMs = 6000)

    assertTrue(dumps.isEmpty(), "expected a process stall to save no dump")
    assertTrue(warnings.isEmpty(), "expected a process stall to write no warning, got $warnings")
  }

  @Test
  fun `a pool exhaustion saves the dump and reports the measured time`() {
    val clock = FakeClock()
    val warnings = mutableListOf<String>()
    val dumps = mutableListOf<Unit>()
    val dumper = newDumper(clock, warnings, dumps)

    // The sampler is stale for 3 s and the watcher woke up on time: the pool itself is unresponsive.
    clock.advanceMs(3000)
    dumper.dumpThreadsIfNeeded(lastSampleNs = 0, oversleptMs = 0)

    assertEquals(1, dumps.size, "expected a pool exhaustion to save one dump")
    assertEquals(1, warnings.size, "expected exactly one warning, got $warnings")
    assertTrue("not responding for 3000 ms (threshold: 2000 ms)" in warnings.single(),
               "expected the warning to report the measured time and the threshold: ${warnings.single()}")
  }
}
