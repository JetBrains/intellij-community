package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService

class UIComponentsList<T : UiComponent>(private val xpath: String,
                                        private val type: Class<T>,
                                        val driver: Driver,
                                        val searchService: SearchService,
                                        val robotProvider: RobotProvider,
                                        private val parentSearchContext: SearchContext) {
  fun list(): List<T> {
    return parentSearchContext.findAll(xpath).mapIndexed { n, c ->
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
  }
}