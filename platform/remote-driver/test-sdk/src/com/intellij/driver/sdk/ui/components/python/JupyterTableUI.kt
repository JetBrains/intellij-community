package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import org.intellij.lang.annotations.Language

fun Finder.jupyterTableOutput(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='JupyterTableOutputComponent']", JupyterTableUiComponent::class.java)

class JupyterTableUiComponent(data: ComponentData) : UiComponent(data) {
  val tableResults = x("//div[@class='TableResultView']", JTableUiComponent::class.java)

  fun getValuesFromTable(): List<String> {
    return tableResults.content().values.flatMap { it.values }
  }
}