package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import javax.swing.JTabbedPane

fun Finder.tabbedPane(locator: QueryBuilder.() -> String = { byType(JTabbedPane::class.java) }) =
  x(JBTabbedPaneUiComponent::class.java, locator)

class JBTabbedPaneUiComponent(data: ComponentData) : UiComponent(data) {
  private val tabbedPaneComponent get() = driver.cast(component, JBTabbedPaneComponent::class)
  private val tabContainer = x { byType("javax.swing.plaf.basic.BasicTabbedPaneUI\$TabContainer") }
  val selectedTabName
    get() = tabbedPaneComponent.run {
      val selectedIndex = getSelectedIndex()
      if (selectedIndex == -1) null
      else getTitleAt(selectedIndex)
    }

  fun tab(name: String): UiComponent = tabContainer.x { byAccessibleName(name) }
}

@Remote("com.intellij.ui.components.JBTabbedPane")
interface JBTabbedPaneComponent : Component {
  fun getTitleAt(index: Int): String
  fun getSelectedIndex(): Int
}