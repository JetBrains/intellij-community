package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.compenents.UiComponent
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


// should
fun <T : UiComponent> T.should(condition: T.() -> Boolean): T {
  return should(Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong()), condition)
}

fun <T : UiComponent> T.should(seconds: Int = DEFAULT_FIND_TIMEOUT_SECONDS, condition: T.() -> Boolean): T {
  return should(Duration.ofSeconds(seconds.toLong()), condition)
}

fun <T : UiComponent> T.should(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong()),
                               condition: T.() -> Boolean): T {
  waitFor(timeout) {
    try {
      this.condition()
    }
    catch (e: Throwable) {
      false
    }
  }
  return this
}