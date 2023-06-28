package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.remote.RemoteComponent
import com.intellij.driver.sdk.ui.remote.SearchContext
import org.intellij.lang.annotations.Language
import java.time.Duration

private const val DEFAULT_FIND_TIMEOUT_SECONDS = 15

interface ComponentFinder {
  val searchContext: SearchContext

  fun find(@Language("xpath") xpath: String, timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): UiComponent {
    return find(xpath, UiComponent::class.java, timeout)
  }

  fun findAll(@Language("xpath") xpath: String): List<UiComponent> {
    return findAll(xpath, UiComponent::class.java)
  }

  // PageObject Support
  fun <T : UiComponent> find(@Language("xpath") xpath: String,
                             uiType: Class<T>,
                             timeout: Duration = Duration.ofSeconds(DEFAULT_FIND_TIMEOUT_SECONDS.toLong())): T {
    waitFor(timeout, errorMessage = "Can't find uiComponent with '$xpath' in ${searchContext.context}") {
      findAll(xpath, uiType).size == 1
    }
    return findAll(xpath, uiType).first()
  }

  fun <T : UiComponent> findAll(@Language("xpath") xpath: String, uiType: Class<T>): List<T> {
    return searchContext.findAll(xpath).map {
      uiType.getConstructor(RemoteComponent::class.java)
        .newInstance(it)
    }
  }
}