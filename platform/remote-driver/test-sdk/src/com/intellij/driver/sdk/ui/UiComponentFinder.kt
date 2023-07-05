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
  fun findOne(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): T {
    return finder.find(xpath, type, timeout)
  }

  fun findOneOrNull(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): T? {
    return try {
      finder.find(xpath, type, timeout)
    }
    catch (e: WaitForException) {
      null
    }
  }

  fun findMany(): List<T> {
    return finder.findAll(xpath, type)
  }

  fun ifShowedOne(action: T.() -> Unit) {
    findMany().singleOrNull()?.action()
  }

  fun waitForOne(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): UiComponentFinder<T> {
    findOne(timeout)
    return this
  }

  fun should(timeout: Int = DEFAULT_FIND_TIMEOUT_SECONDS,
             condition: UiComponentFinder<T>.() -> Boolean): UiComponentFinder<T> {
    return should(Duration.ofSeconds(timeout.toLong()), condition)
  }
  fun should(timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong()),
             condition: UiComponentFinder<T>.() -> Boolean): UiComponentFinder<T> {
    waitFor(timeout) {
      this.condition()
    }
    return this
  }

  fun waitForExactAmount(amount: Int, timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): UiComponentFinder<T> {
    waitFor(timeout, errorMessage = "Failed to wait for $amount uiComponents($xpath) in ${finder.searchContext.context}") {
      findMany().size == amount
    }
    return this
  }

  fun click() {
    findOne().click()
  }

  fun doubleClick() {
    findOne().doubleClick()
  }
  fun rightClick() {
    findOne().rightClick()
  }
}