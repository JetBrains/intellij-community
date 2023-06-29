package com.intellij.driver.sdk.ui

import org.intellij.lang.annotations.Language
import java.time.Duration


fun Finder.x(@Language("xpath") xpath: String): UiComponentFinder<UiComponent> {
  return UiComponentFinder(xpath, UiComponent::class.java, this)
}

fun <T : UiComponent> Finder.x(@Language("xpath") xpath: String, type: Class<T>): UiComponentFinder<T> {
  return UiComponentFinder(xpath, type, this)
}

class UiComponentFinder<T : UiComponent>(private val xpath: String, private val type: Class<T>, private val finder: Finder) {
  fun one(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): T {
    return finder.find(xpath, type, timeout)
  }

  fun oneOrNull(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): T? {
    return try {
      finder.find(xpath, type, timeout)
    }
    catch (e: WaitForException) {
      null
    }
  }

  fun many(): List<T> {
    return finder.findAll(xpath, type)
  }

  fun ifShowedOne(action: T.() -> Unit) {
    many().singleOrNull()?.action()
  }

  fun waitFor(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): UiComponentFinder<T> {
    one(timeout)
    return this
  }

  fun waitForExactAmount(amount: Int, timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): UiComponentFinder<T> {
    waitFor(timeout, errorMessage = "Failed to wait for $amount uiComponents($xpath) in ${finder.searchContext.context}") {
      many().size == amount
    }
    return this
  }
}