package com.intellij.driver.sdk

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun waitFor(
  duration: Duration = 5.seconds,
  interval: Duration = 2.seconds,
  errorMessage: String = "",
  condition: () -> Boolean
) {
  val endTime = System.currentTimeMillis() + duration.inWholeMilliseconds
  var now = System.currentTimeMillis()
  while (now < endTime && condition().not()) {
    Thread.sleep(interval.inWholeMilliseconds)
    now = System.currentTimeMillis()
  }
  if (condition().not()) {
    throw WaitForException(duration, errorMessage)
  }
}

class WaitForException(duration: Duration, errorMessage: String) : IllegalStateException("Timeout($duration): $errorMessage")