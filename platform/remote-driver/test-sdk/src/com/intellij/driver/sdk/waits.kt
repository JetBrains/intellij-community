package com.intellij.driver.sdk

import com.intellij.driver.sdk.ui.components.UiComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun waitFor(
  duration: Duration = 5.seconds,
  interval: Duration = 1.seconds,
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

fun <T : UiComponent> T.wait(duration: Duration): T {
  Thread.sleep(duration.inWholeMilliseconds)
  return this
}