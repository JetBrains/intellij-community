// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

class RequestTelemetryData(timestamp: Long) {
  val delta: Long

  init {
    val timeOfReceive = getCurrentTime()
    delta = timeOfReceive - timestamp
  }

  fun deltaAdjustedTime(): Long {
    return currentTimeWithAdjustment(delta)
  }
}