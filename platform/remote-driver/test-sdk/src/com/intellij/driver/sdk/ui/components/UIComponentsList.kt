package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.*
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.waitFor
import com.intellij.openapi.diagnostic.Logger
import kotlin.time.Duration

class UIComponentsList<T : UiComponent>(
  private val xpath: String,
  private val type: Class<T>,
  val driver: Driver,
  val searchService: SearchService,
  val robotProvider: RobotProvider,
  private val parentSearchContext: SearchContext,
) {

  companion object {
    private val LOG get() = Logger.getInstance(UIComponentsList::class.java)

    /**
     * Searches for a non-empty list of UI components based on the given XPath until timeout hits.
     */
    fun Finder.waitAny(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, init: QueryBuilder.() -> String): List<UiComponent> {
      return waitFor(message, timeout,
                     getter = { xx(init = init).list() },
                     checker = { it.isNotEmpty() })
    }

    /**
     * Waits for some ui components matching the given XPath.
     */
    fun <T : UiComponent> Finder.waitAny(message: String? = null, type: Class<T>, timeout: Duration = DEFAULT_FIND_TIMEOUT, init: QueryBuilder.() -> String): List<T> {
      return waitFor(message, timeout,
                     getter = { xx(type = type, init = init).list() },
                     checker = { it.isNotEmpty() })
    }

    /**
     * Waits until there are no ui components matching the given XPath.
     */
    fun Finder.waitNotFound(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, init: QueryBuilder.() -> String): List<UiComponent> {
      return waitFor(message, timeout,
                     getter = { xx(init = init).list() },
                     checker = { it.isEmpty() })
    }
  }

  fun list(): List<T> {
    LOG.info("Requesting all ${type.simpleName}(s) by xpath = $xpath in ${parentSearchContext.contextAsString}")

    val components = parentSearchContext.findAll(xpath).mapIndexed { n, c ->
      val searchContext = object : SearchContext {
        override val context: String
          get() = parentSearchContext.context + xpath + "[$n]"

        override fun findAll(xpath: String): List<Component> {
          return searchService.findAll(xpath, c)
        }
      }
      type.getConstructor(
        ComponentData::class.java
      ).newInstance(ComponentData(xpath, driver, searchService, robotProvider, searchContext, c))
    }
    LOG.info("Returning ${components.size} ${type.simpleName}(s) by xpath = $xpath" +
             "\n${printableString(components.joinToString(", ") { it.toString() })}")
    return components
  }
}