package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.waitFor
import java.awt.Point
import java.awt.Rectangle
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

  /**
   * Clicks the header of the [title] tab, then waits until that tab is selected.
   *
   * The click goes to the tabbed pane at the bounds of the tab, not to the header component.
   * So it still lands when the pane creates its tab headers again.
   */
  fun clickTab(title: String) {
    val tabCenter = waitFor("The '$title' tab is on $this", getter = { tabCenterOrNull(title) }, checker = { it != null })!!
    click(tabCenter)
    waitFor("The '$title' tab is selected on $this", getter = { selectedTabName }, checker = { it == title })
  }

  private fun tabCenterOrNull(title: String): Point? = driver.withContext(OnDispatcher.EDT) {
    val pane = tabbedPaneComponent
    val index = pane.indexOfTab(title)
    if (index < 0) null else pane.getBoundsAt(index)?.let { Point(it.centerX.toInt(), it.centerY.toInt()) }
  }
}

@Remote("com.intellij.ui.components.JBTabbedPane")
interface JBTabbedPaneComponent : Component {
  fun getTitleAt(index: Int): String
  fun getSelectedIndex(): Int
  fun indexOfTab(title: String): Int
  fun getBoundsAt(index: Int): Rectangle?
}