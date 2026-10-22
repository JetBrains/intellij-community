// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TestTimeSource

/**
 * A fake [ProcessClock] where `sleep` and `delay` advance the time by the requested duration plus the next scripted lateness.
 * A lateness above zero models a thread that did not get CPU time on schedule and therefore overslept.
 */
internal class FakeClock(vararg lateMsPerSleep: Long) : ProcessClock {
  private val lateness = lateMsPerSleep.toMutableList()

  private val timeSource = TestTimeSource()

  private val origin = timeSource.markNow()

  /** The time since time zero, in nanoseconds. */
  val nowNs: Long
    get() = origin.elapsedNow().inWholeNanoseconds

  /** The total GC time the clock reports. A test sets it to check the GC delta in a warning. */
  var gcTimeMs: Long = 0

  fun advanceMs(ms: Long) {
    timeSource += ms.milliseconds
  }

  /** The point of this clock at [ns] nanoseconds after time zero. */
  fun markAt(ns: Long): ComparableTimeMark = origin + ns.nanoseconds

  override fun now(): ComparableTimeMark = timeSource.markNow()

  override fun sleep(duration: Duration) {
    val lateMs = if (lateness.isEmpty()) 0 else lateness.removeFirst()
    advanceMs(duration.inWholeMilliseconds + lateMs)
  }

  override suspend fun delay(duration: Duration) {
    sleep(duration)
  }

  override fun gcTime(): Duration = gcTimeMs.milliseconds
}
