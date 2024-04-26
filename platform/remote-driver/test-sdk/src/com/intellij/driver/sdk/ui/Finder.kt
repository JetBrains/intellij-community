package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotServiceProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import org.intellij.lang.annotations.Language

internal const val DEFAULT_FIND_TIMEOUT_SECONDS = 15

interface Finder {
  val driver: Driver
  val searchService: SearchService
  val robotServiceProvider: RobotServiceProvider
  val searchContext: SearchContext

  val isRemoteIdeMode: Boolean
    get() = driver.isRemoteIdeMode

  fun x(@Language("xpath") xpath: String): UiComponent {
    return UiComponent(ComponentData(xpath, driver, searchService, robotServiceProvider, searchContext, null))
  }

  fun <T : UiComponent> x(@Language("xpath") xpath: String, type: Class<T>): T {
    return type.getConstructor(
      ComponentData::class.java
    ).newInstance(ComponentData(xpath, driver, searchService, robotServiceProvider, searchContext, null))
  }

  fun xx(@Language("xpath") xpath: String): UIComponentsList<UiComponent> {
    return UIComponentsList(xpath, UiComponent::class.java, driver, searchService, robotServiceProvider, searchContext)
  }

  fun <T : UiComponent> xx(@Language("xpath") xpath: String, type: Class<T>): UIComponentsList<T> {
    return UIComponentsList(xpath, type, driver, searchService, robotServiceProvider, searchContext)
  }
}

interface SearchContext {
  val context: String
  fun findAll(xpath: String): List<Component>
}