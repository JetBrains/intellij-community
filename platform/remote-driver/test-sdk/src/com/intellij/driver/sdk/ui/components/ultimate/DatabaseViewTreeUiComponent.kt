package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DATABASE_VIEW_TREE_COMPONENT_CLASS = "com.intellij.database.view.DatabaseViewTreeComponent"

class DatabaseViewTreeUiComponent(data: ComponentData) : JTreeUiComponent(data) {

  fun hasDataSourceContaining(name: String): Boolean =
    collectExpandedPaths().any { it.path.last().contains(name, ignoreCase = true) }

  fun waitForDataSourceContaining(name: String, timeout: Duration = 30.seconds) {
    waitFor("'$name' datasource to appear in database tree", timeout) {
      hasDataSourceContaining(name)
    }
  }

  @OptIn(ManualWaitForIndicators::class)
  fun waitForIntrospectionToFinish(timeout: Duration = 5.minutes) {
    driver.waitForIndicators(timeout)
  }

  fun isSelectedPathContaining(text: String): Boolean =
    collectSelectedPaths().any { path -> path.path.any { it.contains(text, ignoreCase = true) } }

  fun waitForSelectedPathContaining(text: String, timeout: Duration = 30.seconds) {
    waitFor("'$text' selected in database tree", timeout) { isSelectedPathContaining(text) }
  }
}

fun Finder.databaseViewTree(): DatabaseViewTreeUiComponent =
  x(xQuery { byType(DATABASE_VIEW_TREE_COMPONENT_CLASS) }, DatabaseViewTreeUiComponent::class.java)
