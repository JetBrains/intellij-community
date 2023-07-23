package com.intellij.driver.sdk

import java.time.Duration

fun waitFor(
  duration: Duration = Duration.ofSeconds(5),
  interval: Duration = Duration.ofSeconds(2),
  errorMessage: String = "",
  condition: () -> Boolean
) {
  val endTime = System.currentTimeMillis() + duration.toMillis()
  var now = System.currentTimeMillis()
  while (now < endTime && condition().not()) {
    Thread.sleep(interval.toMillis())
    now = System.currentTimeMillis()
  }
  if (condition().not()) {
    throw WaitForException(duration, errorMessage)
  }
}

class WaitForException(duration: Duration, errorMessage: String) : IllegalStateException("Timeout($duration): $errorMessage")