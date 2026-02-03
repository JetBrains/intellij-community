// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

class EventsIdentityWindowThrottle(private var threshold: Int, private var alertThreshold: Int, private val periodMs: Long) {
  private var keyToCount = hashMapOf<String, EventDescriptor>()

  fun setThreshold(newThreshold: Int) {
    threshold = newThreshold
  }

  fun setAlertThreshold(newThreshold: Int) {
    alertThreshold = newThreshold
  }

  @Synchronized
  fun tryPass(key: String, now: Long): EventRateThrottleResult {
    val period = now / periodMs
    val descriptor = keyToCount[key]
    if (descriptor == null) {
      keyToCount[key] = EventDescriptor(lastPeriod = period)
      return EventRateThrottleResult.ACCEPT
    }

    if (period != descriptor.lastPeriod) {
      descriptor.resetCounter(period)
      return EventRateThrottleResult.ACCEPT
    }

    if (descriptor.count < threshold) {
      val alert = isAlert(descriptor)
      descriptor.incrementCounter()
      return if (alert) EventRateThrottleResult.ALERT else EventRateThrottleResult.ACCEPT
    }
    else if (descriptor.count == threshold) {
      descriptor.incrementCounter()
      return EventRateThrottleResult.DENY_AND_REPORT
    }
    return EventRateThrottleResult.DENY
  }

  private fun isAlert(descriptor: EventDescriptor) = alertThreshold > 0 && descriptor.count == alertThreshold
}

private data class EventDescriptor(var count: Int = 1, var lastPeriod: Long) {
  fun incrementCounter() {
    count++
  }

  fun resetCounter(period: Long) {
    count = 1
    lastPeriod = period
  }
}