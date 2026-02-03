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