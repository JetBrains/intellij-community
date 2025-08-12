package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.*
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
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
    replaceCellRendererReader { driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as RefWrapper).getRef().rdTarget) }
  }

/** Locates JBList element */
fun Finder.jBlist(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JBList']",
                                                                JListUiComponent::class.java)

open class JListUiComponent(data: ComponentData) : UiComponent(data) {
  private var cellRendererReaderSupplier: ((JListFixtureRef) -> CellRendererReader)? = null
  private val fixture by lazy {
    driver.new(JListFixtureRef::class, robot, component).apply {
      cellRendererReaderSupplier?.let { replaceCellRendererReader(it(this)) }
    }
  }

  val listComponent: JListComponent get() = driver.cast(component, JListComponent::class)

  open val items: List<String>
    get() = fixture.collectItems()

  val rawItems: List<String>
    get() = fixture.collectRawItems()

  open val selectedItems: List<String>
    get() = fixture.collectSelectedItems()

  fun replaceCellRendererReader(readerSupplier: (JListFixtureRef) -> CellRendererReader) {
    cellRendererReaderSupplier = readerSupplier
  }

  fun clickItem(itemText: String, fullMatch: Boolean = true, trimmed: Boolean = false, offset: Point? = null) {
    findItemIndex(itemText, fullMatch, trimmed)?.let { index ->
      clickItemAtIndex(index, offset)
    } ?: throw IllegalArgumentException("item with text $itemText not found, all items: ${items.joinToString(", ")}")
  }

  fun doubleClickItem(itemText: String, fullMatch: Boolean = true) {
    findItemIndex(itemText, fullMatch)?.let { index ->
      val cellBounds = getCellBounds(index)
      doubleClick(Point(cellBounds.centerX.toInt(), cellBounds.centerY.toInt()))
    }
  }

  fun hoverItem(itemText: String, fullMatch: Boolean = true, trimmed: Boolean = false, offset: Point? = null) {
    findItemIndex(itemText, fullMatch, trimmed)?.let { index ->
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


  fun clickItemAtIndex(index: Int, offset: Point? = null) {
    if (offset == null) {
      fixture.clickItemAtIndex(index)
    } else {
      val cellBounds = getCellBounds(index)
      val cellOffset = Point(offset.x, offset.y + cellBounds.getY().toInt())
      check(cellBounds.contains(cellOffset)) { "point is out of cell bounds" }
      robot.click(component, cellOffset)
    }
  }

  fun collectIconsAtIndex(index: Int) = fixture.collectIconsAtIndex(index)

  fun hoverItemAtIndex(index: Int) {
    val cellBounds = driver.withContext(OnDispatcher.EDT) { listComponent.getCellBounds(index, index) }
    moveMouse(cellBounds.center)
  }

  fun invokeSelectNextRowAction() {
    driver.invokeAction("List-selectNextRow", component = component)
  }

  fun invokeSelectPrevRowAction() {
    driver.invokeAction("List-selectPreviousRow", component = component)
  }

  fun isSelectedIndex(index: Int) = listComponent.isSelectedIndex(index)

  fun getCellBounds(index: Int): Rectangle =
    driver.withContext(OnDispatcher.EDT) { listComponent.getCellBounds(index, index) }

  fun getComponentAt(index: Int): Component = fixture.getComponentAtIndex(index)

  protected fun findItemIndex(itemText: String, fullMatch: Boolean, trimmed: Boolean = false): Int? =
    fixture.collectItems().indexOfFirst {
      val text = if (trimmed) it.trim() else it
      if (fullMatch) text == itemText
      else text.contains(itemText, true)
    }.takeIf { it != -1 }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JListTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JListFixtureRef {
  fun replaceCellRendererReader(reader: CellRendererReader)
  fun collectItems(): List<String>
  fun collectRawItems(): List<String>
  fun collectSelectedItems(): List<String>
  fun clickItemAtIndex(index: Int)
  fun collectIconsAtIndex(index: Int): List<String>
  fun getComponentAtIndex(index: Int): Component
}

@Remote("javax.swing.JList")
interface JListComponent {
  fun getCellBounds(index0: Int, index1: Int): Rectangle
  fun isSelectedIndex(index: Int): Boolean
}
