package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.waitFor
import java.time.Duration

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