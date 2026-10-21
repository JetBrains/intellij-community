// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [ProcessStallWatcher.tick] with a [FakeClock], so the test spends no time in real sleeps.
 */
internal class ProcessStallWatcherTest {
  private val sliceMs = 250L
  private val slicesPerTick = 4
  private val reportThresholdMs = 1000L

  private fun newWatcher(clock: FakeClock, warnings: MutableList<String>, thresholdMs: Long = reportThresholdMs): ProcessStallWatcher {
    return ProcessStallWatcher(
      sliceMs = sliceMs,
      slicesPerTick = slicesPerTick,
      reportThresholdMs = thresholdMs,
      clock = clock,
      warn = warnings::add,
    )
  }

  @Test
  fun `on-time slices write no warning`() {
    val clock = FakeClock()
    val warnings = mutableListOf<String>()
    val watcher = newWatcher(clock, warnings)

    watcher.tick()

    assertTrue(warnings.isEmpty(), "expected no warning, got $warnings")
  }

  @Test
  fun `one slice that wakes up 1200 ms late warns once with the GC delta`() {
    val clock = FakeClock(0, 1200, 0, 0)
    val warnings = mutableListOf<String>()
    val watcher = newWatcher(clock, warnings)

    clock.gcTime = 350
    watcher.tick()

    assertEquals(1, warnings.size, "expected exactly one warning, got $warnings")
    assertTrue("1200 ms" in warnings.single(), "expected the warning to report the lateness: ${warnings.single()}")
    assertTrue("GC time: 350 ms" in warnings.single(), "expected the warning to report the GC delta: ${warnings.single()}")
    assertTrue(warnings.single().startsWith("Process stall:"), "unexpected warning text: ${warnings.single()}")
  }

  @Test
  fun `two slices that wake up 600 ms late each warn once with the summed lateness`() {
    val clock = FakeClock(600, 0, 600, 0)
    val warnings = mutableListOf<String>()
    val watcher = newWatcher(clock, warnings)

    watcher.tick()

    assertEquals(1, warnings.size, "expected exactly one warning per tick, got $warnings")
    assertTrue("1200 ms" in warnings.single(), "expected the warning to report the summed lateness: ${warnings.single()}")
  }

  @Test
  fun `lateness below the threshold writes no warning`() {
    val clock = FakeClock(100, 100, 100, 100)
    val warnings = mutableListOf<String>()
    val watcher = newWatcher(clock, warnings)

    watcher.tick()

    assertTrue(warnings.isEmpty(), "expected lateness below the threshold to write no warning, got $warnings")
  }

  @Test
  fun `a lower threshold reports a shorter stall`() {
    val clock = FakeClock(100, 100, 100, 100)
    val warnings = mutableListOf<String>()
    val watcher = newWatcher(clock, warnings, thresholdMs = 400)

    watcher.tick()

    assertEquals(1, warnings.size, "expected the 400 ms threshold to report a 400 ms stall, got $warnings")
    assertTrue("400 ms" in warnings.single(), "expected the warning to report the lateness: ${warnings.single()}")
  }
}
