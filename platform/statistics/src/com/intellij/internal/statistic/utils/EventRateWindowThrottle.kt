// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

class EventsRateWindowThrottle(private var threshold: Int, private val periodMs: Long, startTime: Long) {
  private var count: Int = 0
  private var lastPeriod: Long = startTime / periodMs

  @Synchronized
  fun tryPass(now: Long): EventRateThrottleResult {
    val period = now / periodMs
    if (period != lastPeriod) {
      resetCounter(period)
      return EventRateThrottleResult.ACCEPT
    }

    if (count < threshold) {
      incrementCounter()
      return EventRateThrottleResult.ACCEPT
    }
    else if (count == threshold) {
      incrementCounter()
      return EventRateThrottleResult.DENY_AND_REPORT
    }
    return EventRateThrottleResult.DENY
  }

  private fun incrementCounter() {
    count++
  }

  private fun resetCounter(period: Long) {
    count = 1
    lastPeriod = period
  }
}

enum class EventRateThrottleResult {
  ACCEPT, DENY_AND_REPORT, DENY
}