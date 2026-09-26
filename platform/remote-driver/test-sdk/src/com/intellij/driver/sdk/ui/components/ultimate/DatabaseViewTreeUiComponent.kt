package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DatabaseViewTreeUiComponent(data: ComponentData) : JTreeUiComponent(data) {

  fun hasDataSourceContaining(name: String): Boolean =
    collectExpandedPaths().any { it.path.last().contains(name, ignoreCase = true) }

  fun waitForDataSourceContaining(name: String, timeout: Duration = 30.seconds) {
    waitFor("'$name' datasource to appear in database tree", timeout) {
      hasDataSourceContaining(name)
    }
  }

  fun isSelectedPathContaining(text: String): Boolean =
    collectSelectedPaths().any { path -> path.path.any { it.contains(text, ignoreCase = true) } }

  fun waitForSelectedPathContaining(text: String, timeout: Duration = 30.seconds) {
    waitFor("'$text' selected in database tree", timeout) { isSelectedPathContaining(text) }
  }
}

fun Finder.databaseViewTree(): DatabaseViewTreeUiComponent =
  x("//div[@class='DatabaseViewTreeComponent']", DatabaseViewTreeUiComponent::class.java)
