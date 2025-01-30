package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration

// should
infix fun <T : UiComponent> T.should(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

// should not
infix fun <T : UiComponent> T.shouldNot(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = { !condition() })
}

// should
infix fun <T : UiComponent> T.shouldBe(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

infix fun <T : UiComponent> T.shouldBeNoExceptions(condition: T.() -> Unit): T {
  return shouldBeNoExceptions(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}


fun <T : UiComponent> T.shouldBe(message: String, condition: T.() -> Boolean, timeout: Duration): T {
  return should(message = message, timeout = timeout, condition = condition)
}

fun <T : UiComponent> T.shouldBe(message: String, condition: T.() -> Boolean): T {
  return should(message = message, timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.shouldBe(condition: T.() -> Boolean, timeout: Duration): T {
  return should(timeout = timeout, condition = condition)
}

// should
infix fun <T : UiComponent> T.shouldHave(condition: T.() -> Boolean): T {
  return should(timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.shouldHave(message: String, condition: T.() -> Boolean): T {
  return should(message = message, timeout = DEFAULT_FIND_TIMEOUT, condition = condition)
}

fun <T : UiComponent> T.shouldHave(condition: T.() -> Boolean, timeout: Duration): T {
  return should(timeout = timeout, condition = condition)
}

fun <T : UiComponent> T.shouldHave(message: String, condition: T.() -> Boolean, timeout: Duration): T {
  return should(message = message, timeout = timeout, condition = condition)
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

fun <T : UiComponent> T.should(message: String? = null,
                               timeout: Duration = DEFAULT_FIND_TIMEOUT,
                               condition: T.() -> Boolean): T {
  var lastException: Throwable? = null
  try {
    waitFor(message, timeout) {
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

val visible: UiComponent.() -> Boolean = { isVisible() }

val enabled: UiComponent.() -> Boolean = { isEnabled() }

val notEnabled: UiComponent.() -> Boolean = { !isEnabled() }

val present: UiComponent.() -> Boolean = { present() }

val notPresent: UiComponent.() -> Boolean = { notPresent() }

fun haveText(value: String): UiComponent.() -> Boolean = { hasText(value) }

fun haveButton(value: String): UiComponent.() -> Boolean = { button(value).present() }

