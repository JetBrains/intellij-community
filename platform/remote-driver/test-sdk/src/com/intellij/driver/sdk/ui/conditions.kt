package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// should
infix fun <T : UiComponent> T.should(condition: T.() -> Boolean): T {
  return should(DEFAULT_FIND_TIMEOUT_SECONDS.seconds, condition)
}

// should
infix fun <T : UiComponent> T.shouldBe(condition: T.() -> Boolean): T {
  return should(DEFAULT_FIND_TIMEOUT_SECONDS.seconds, condition)
}

fun <T : UiComponent> T.shouldBe(condition: T.() -> Boolean, timeout: Duration): T {
  return should(timeout, condition)
}

// should
infix fun <T : UiComponent> T.shouldHave(condition: T.() -> Boolean): T {
  return should(DEFAULT_FIND_TIMEOUT_SECONDS.seconds, condition)
}

fun <T : UiComponent> T.shouldHave(condition: T.() -> Boolean, timeout: Duration): T {
  return should(timeout, condition)
}

fun <T : UiComponent> T.should(seconds: Int = DEFAULT_FIND_TIMEOUT_SECONDS, condition: T.() -> Boolean): T {
  return should(seconds.seconds, condition)
}

fun <T : UiComponent> T.should(timeout: Duration = DEFAULT_FIND_TIMEOUT_SECONDS.seconds,
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

val visible: UiComponent.() -> Boolean = { isVisible() }

fun text(value: String): UiComponent.() -> Boolean = { hasText(value) }