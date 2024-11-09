package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.CellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JList

/** Locates JList element */
fun Finder.list(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JList::class.java.name) }, JListUiComponent::class.java)

fun Finder.list(locator: QueryBuilder.() -> String) = x(JListUiComponent::class.java) {locator()}

fun Finder.accessibleList(locator: QueryBuilder.() -> String = { byType(JList::class.java) }) =
  x(JListUiComponent::class.java) { locator() }.apply {
    replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class))
  }

/** Locates JBList element */
fun Finder.jBlist(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JBList']",
                                                                JListUiComponent::class.java)

open class JListUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy { driver.new(JListFixtureRef::class, robot, component) }

  val listComponent: JListComponent get() = driver.cast(component, JListComponent::class)

  val items: List<String>
    get() = fixture.collectItems()

  val rawItems: List<String>
    get() = fixture.collectRawItems()

  val selectedItems: List<String>
    get() = fixture.collectSelectedItems()

  fun replaceCellRendererReader(reader: CellRendererReader) {
    fixture.replaceCellRendererReader(reader)
  }

  fun clickItem(itemText: String, fullMatch: Boolean = true, offset: Point? = null) {
    findItemIndex(itemText, fullMatch)?.let { index ->
      if (offset == null) {
        fixture.clickItemAtIndex(index)
      } else {
        val cellBounds = getCellBounds(index)
        val cellOffset = Point(offset.x, offset.y + cellBounds.getY().toInt())
        check(cellBounds.contains(cellOffset)) { "point is out of cell bounds" }
        robot.click(component, cellOffset)
      }
    } ?: throw IllegalArgumentException("item with text $itemText not found, all items: ${items.joinToString(", ")}")
  }

  fun doubleClickItem(itemText: String, fullMatch: Boolean = true) {
    findItemIndex(itemText, fullMatch)?.let { index ->
      val cellBounds = getCellBounds(index)
      doubleClick(Point(cellBounds.getCenterX().toInt(), cellBounds.getCenterY().toInt()))
    }
  }

  fun hoverItem(itemText: String, fullMatch: Boolean = true, offset: Point? = null) {
    findItemIndex(itemText, fullMatch)?.let { index ->
      if (offset == null) {
        hoverItemAtIndex(index)
      } else {
        val cellBounds = getCellBounds(index)
        val cellOffset = Point(offset.x, offset.y + cellBounds.getY().toInt())
        check(cellBounds.contains(cellOffset)) { "point is out of cell bounds" }
        robot.moveMouse(component, cellOffset)
      }
    } ?: throw IllegalArgumentException("item with text $itemText not found, all items: ${items.joinToString(", ")}")
  }


  fun clickItemAtIndex(index: Int) = fixture.clickItemAtIndex(index)

  fun collectIconsAtIndex(index: Int) = fixture.collectIconsAtIndex(index)

  fun hoverItemAtIndex(index: Int) {
    val cellBounds = driver.withContext(OnDispatcher.EDT) { listComponent.getCellBounds(index, index) }
    moveMouse(Point(cellBounds.getX().toInt(), cellBounds.getY().toInt()))
  }

  fun invokeSelectNextRowAction() {
    driver.invokeAction("List-selectNextRow", component = component)
  }

  fun invokeSelectPrevRowAction() {
    driver.invokeAction("List-selectPreviousRow", component = component)
  }

  protected fun findItemIndex(itemText: String, fullMatch: Boolean): Int? =
    fixture.collectItems().indexOfFirst {
      if (fullMatch) it == itemText
      else it.contains(itemText, true)
    }.let {
      if (it == -1) null
      else it
    }

  private fun getCellBounds(index: Int): Rectangle =
    driver.withContext(OnDispatcher.EDT) { listComponent.getCellBounds(index, index) }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JListTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JListFixtureRef {
  fun replaceCellRendererReader(reader: CellRendererReader)
  fun collectItems(): List<String>
  fun collectRawItems(): List<String>
  fun collectSelectedItems(): List<String>
  fun clickItemAtIndex(index: Int)
  fun collectIconsAtIndex(index: Int): List<String>
}

@Remote("javax.swing.JList")
interface JListComponent {
  fun getCellBounds(index0: Int, index1: Int): Rectangle
}
