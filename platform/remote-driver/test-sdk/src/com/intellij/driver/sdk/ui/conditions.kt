package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration

fun <T : UiComponent> T.shouldNot(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = { !condition() })
}

fun <T : UiComponent> T.shouldBe(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.shouldBe(message: String, condition: T.() -> Boolean, timeout: Duration): T {
  return should(message = message, timeout = timeout, condition = condition)
}

fun <T : UiComponent> T.shouldBe(message: String, condition: T.() -> Boolean): T {
  return should(message = message, timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.shouldBeNoExceptions(
  message: String? = null,
  timeout: Duration = DEFAULT_FIND_TIMEOUT,
  condition: T.() -> Unit,
): T {
  var lastException: Throwable? = null
  try {
    waitFor(message, timeout) {
      try {
        this.condition()
        true
      }
      catch (e: Throwable) {
        lastException = e
        false
      }
    }
  }
  catch (e: WaitForException) {
    lastException?.let { throw it }
    throw WaitForException(e.timeout, e.errorMessage)
  }
  return this
}

fun <T : UiComponent> T.should(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.should(message: String? = null,
                               timeout: Duration = DEFAULT_FIND_TIMEOUT,
                               condition: T.() -> Boolean): T {
  return should(message, timeout, null, condition)
}

fun <T : UiComponent> T.should(message: String? = null,
                               timeout: Duration = DEFAULT_FIND_TIMEOUT,
                               errorMessage: (() -> String)? = null,
                               condition: T.() -> Boolean): T {
  var lastException: Throwable? = null
  try {
    waitFor(message, timeout, errorMessage = errorMessage) {
      try {
        this.condition()
      }
      catch (e: Throwable) {
        lastException = e
        false
      }
    }
  } catch (e: WaitForException){
    throw WaitForException(e.timeout, e.errorMessage, lastException)
  }
  return this
}

fun JListUiComponent.shouldBeEqualTo(expected: List<String>, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): JListUiComponent {
  var lastItems: List<String>? = null
  return should(message ?: "items should be equal to $expected", timeout, { "expected: $expected, but found: $lastItems" }) {
    lastItems = items
    lastItems == expected
  }
}

val enabled: UiComponent.() -> Boolean = { isEnabled() }

val notEnabled: UiComponent.() -> Boolean = { !isEnabled() }

val present: UiComponent.() -> Boolean = { present() }

val notPresent: UiComponent.() -> Boolean = { notPresent() }

val focusOwner: UiComponent.() -> Boolean = { isFocusOwner() }
