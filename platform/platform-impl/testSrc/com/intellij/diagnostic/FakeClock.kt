// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import java.util.concurrent.TimeUnit

/**
 * A fake [ProcessClock] where `sleep(ms)` and `delay(ms)` advance the time by `ms` plus the next scripted lateness.
 * A lateness above zero models a thread that did not get CPU time on schedule and therefore overslept.
 */
internal class FakeClock(vararg lateMsPerSleep: Long) : ProcessClock {
  private val lateness = lateMsPerSleep.toMutableList()

  var nowNs: Long = 0
    private set

  /** The total GC time the clock reports. A test sets it to check the GC delta in a warning. */
  var gcTime: Long = 0

  fun advanceMs(ms: Long) {
    nowNs += TimeUnit.MILLISECONDS.toNanos(ms)
  }

  override fun nanoTime(): Long = nowNs

  override fun sleep(ms: Long) {
    val lateMs = if (lateness.isEmpty()) 0 else lateness.removeFirst()
    advanceMs(ms + lateMs)
  }

  override suspend fun delay(ms: Long) {
    sleep(ms)
  }

  override fun gcTimeMs(): Long = gcTime
}
