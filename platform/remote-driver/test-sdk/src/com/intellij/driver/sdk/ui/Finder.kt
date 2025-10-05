package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_FIND_TIMEOUT = 15.seconds

interface Finder {
  val driver: Driver
  val searchService: SearchService
  val robotProvider: RobotProvider
  val searchContext: SearchContext

  val isRemDevMode: Boolean
    get() = driver.isRemDevMode

  /**
   * Creates UiComponent, the actual component will be requested lazily.
   */
  fun x(@Language("xpath") xpath: String): UiComponent {
    return UiComponent(ComponentData(xpath, driver, searchService, robotProvider, searchContext, null))
  }

  fun x(init: QueryBuilder.() -> String): UiComponent {
    return x(xQuery { init() })
  }

  fun <T : UiComponent> x(type: Class<T>, init: QueryBuilder.() -> String): T {
    return x(xQuery { init() }, type)
  }

  fun <T : UiComponent> x(@Language("xpath") xpath: String, type: Class<T>): T {
    val constructor = type.getConstructor(
      ComponentData::class.java
    )
    // support private Kotlin classes declared in the same file as test
    constructor.isAccessible = true
    return constructor.newInstance(ComponentData(xpath, driver, searchService, robotProvider, searchContext, null))
  }

  /**
   * Provides a list of UI components based on the given XPath. Actual components list is requested lazily.
   */
  fun xx(@Language("xpath") xpath: String): UIComponentsList<UiComponent> {
    return UIComponentsList(xpath, UiComponent::class.java, driver, searchService, robotProvider, searchContext)
  }

  fun xx(init: QueryBuilder.() -> String): UIComponentsList<UiComponent> {
    return UIComponentsList(xQuery { init() }, UiComponent::class.java, driver, searchService, robotProvider, searchContext)
  }

  fun <T : UiComponent> xx(type: Class<T>, init: QueryBuilder.() -> String): UIComponentsList<T> {
    return UIComponentsList(xQuery { init() }, type, driver, searchService, robotProvider, searchContext)
  }

  fun <T : UiComponent> xx(@Language("xpath") xpath: String, type: Class<T>): UIComponentsList<T> {
    return UIComponentsList(xpath, type, driver, searchService, robotProvider, searchContext)
  }
}

interface SearchContext {
  val context: String
  val contextAsString: String
    get() = if (context == "") "global context" else context

  fun findAll(xpath: String): List<Component>
}