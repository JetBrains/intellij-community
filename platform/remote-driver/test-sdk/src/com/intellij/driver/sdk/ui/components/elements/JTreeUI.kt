package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.driver.sdk.remoteDev.*
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.CellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.Icon
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import java.awt.Point
import javax.swing.JTree
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Finder.tree(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JTree::class.java) }, JTreeUiComponent::class.java)

fun Finder.accessibleTree(locator: QueryBuilder.() -> String = { byType(JTree::class.java) }) =
  x(xQuery { locator() }, JTreeUiComponent::class.java).apply {
    replaceCellRendererReader { driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as RefWrapper).getRef().rdTarget) }
  }

open class JTreeUiComponent(data: ComponentData) : UiComponent(data) {
  private val treeComponent get() = driver.cast(component, JTreeComponent::class)
  private var cellRendererReaderSupplier: ((JTreeFixtureRef) -> CellRendererReader)? = null
  val fixture: JTreeFixtureRef
    get() = driver.new(JTreeFixtureRef::class, robot, component).apply {
      cellRendererReaderSupplier?.let { replaceCellRendererReader(it(this)) }
    }

  fun replaceCellRendererReader(readerSupplier: (JTreeFixtureRef) -> CellRendererReader) {
    cellRendererReaderSupplier = readerSupplier
  }

  fun clickRow(row: Int, point: Point? = null) {
    if (point != null) {
      click(translateRowPoint(row, point))
    } else {
      fixture.clickRow(row)
    }
  }
  fun clickRow(point: Point? = null, predicate: (String) -> Boolean) {
    waitForNodesLoaded()
    findRow(predicate)?.let {
      clickRow(it, point)
    } ?: throw PathNotFoundException("row not found")
  }

  fun rightClickRow(row: Int, point: Point? = null) {
    if (point != null) {
      rightClick(translateRowPoint(row, point))
    } else {
      fixture.rightClickRow(row)
    }
  }
  fun rightClickRow(predicate: (String) -> Boolean) {
    waitForNodesLoaded()
    findRow(predicate)?.let {
      rightClickRow(it)
    } ?: throw PathNotFoundException("row not found")
  }
  fun doubleClickRow(row: Int, point: Point? = null) {
    if (point != null) {
      doubleClick(translateRowPoint(row, point))
    } else {
      fixture.doubleClickRow(row)
    }
  }
  fun doubleClickRow(point: Point? = null, predicate: (String) -> Boolean) {
    waitForNodesLoaded()
    findRow(predicate)?.let {
      doubleClickRow(it, point)
    } ?: throw PathNotFoundException("row not found")
  }
  fun clickPath(vararg path: String, fullMatch: Boolean = true) {
    waitForNodesLoaded()
    expandPath(*path.sliceArray(0..path.lastIndex - 1), fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      clickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  fun rightClickPath(vararg path: String, fullMatch: Boolean = true) {
    waitForNodesLoaded()
    expandPath(*path.sliceArray(0..path.lastIndex - 1), fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      rightClickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  fun doubleClickPath(vararg path: String, fullMatch: Boolean = true) {
    waitForNodesLoaded()
    expandPath(*path.sliceArray(0..path.lastIndex - 1), fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      doubleClickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  private fun selectPathWithEnter(vararg path: String, fullMatch: Boolean = true) {
    expandPath(*path.sliceArray(0..path.lastIndex - 1), fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      clickRow(it.row)
      keyboard { enter() }
    } ?: throw PathNotFoundException(path.toList())
  }

  fun expandAll(timeout: Duration = 5.seconds) {
    waitForNodesLoaded()
    fixture.expandAll(timeout.inWholeMilliseconds.toInt())
  }

  fun expandPath(vararg path: String, fullMatch: Boolean = true) {
    for (i in path.indices) {
      waitForNodesLoaded(10.seconds)
      val subPath = path.sliceArray(0..i)
      findExpandedPath(*subPath, fullMatch = fullMatch)?.let {
        driver.withContext(OnDispatcher.EDT) { treeComponent.expandRow(it.row) }
        wait(1.seconds) // wait expand
      } ?: throw PathNotFoundException(path.toList())
    }
    waitForNodesLoaded(10.seconds)
  }

  fun expandPathWithEnter(vararg path: String, fullMatch: Boolean = true) = waitFor("Expand path by enter '${path.toList()}'") {
    try {
      val expandedPath = mutableListOf<String>()
      path.forEach {
        findExpandedPaths(*(expandedPath + listOf(it)).toTypedArray(), fullMatch = fullMatch)
          .ifEmpty {
            selectPathWithEnter(*expandedPath.toTypedArray(), fullMatch = fullMatch)
            findExpandedPaths(*(expandedPath + listOf(it)).toTypedArray(), fullMatch = fullMatch)
          }.ifEmpty {
            throw PathNotFoundException(expandedPath + listOf(it))
          }
        expandedPath.add(it)
      }
      true
    }
    catch (e: PathNotFoundException) {
      false
    }
  }

  fun collapseRow(row: Int) {
    fixture.collapseRow(row)
  }

  fun collapsePath(vararg path: String, fullMatch: Boolean = true) {
    fixture.collapseRow(findExpandedPath(*path, fullMatch = fullMatch)?.row ?: throw PathNotFoundException(path.toList()))
  }

  protected fun findExpandedPath(vararg path: String, fullMatch: Boolean): TreePathToRow? = findExpandedPaths(*path, fullMatch = fullMatch).singleOrNull()

  private fun findExpandedPaths(
    vararg path: String,
    fullMatch: Boolean,
  ): List<TreePathToRow> = collectExpandedPaths().filter { expandedPath ->
    expandedPath.path.size == path.size && expandedPath.path.containsAllNodes(*path, fullMatch = fullMatch)
  }

  fun collectExpandedPaths(): List<TreePathToRow> {
    return fixture.collectExpandedPaths()
  }

  fun collectSelectedPaths(): List<TreePath> = fixture.collectSelectedPaths()

  private fun List<String>.containsAllNodes(vararg treePath: String, fullMatch: Boolean): Boolean = zip(treePath).all {
    if (fullMatch) {
      it.first.equals(it.second, true)
    }
    else {
      it.first.contains(it.second, true)
    }
  }

  fun pathExists(vararg path: String): Boolean {
    expandPath(*path, fullMatch = false)
    return findExpandedPath(*path, fullMatch = false) != null
  }

  fun collectIconsAtRow(row: Int): List<Icon> = fixture.collectIconsAtRow(row)

  fun getComponentAtRow(row: Int): Component = fixture.getComponentAtRow(row)

  fun waitForNodesLoaded(timeout: Duration = 5.seconds) {
    waitFor("tree nodes are loaded", timeout) { fixture.areTreeNodesLoaded() }
  }

  private fun findRow(predicate: (String) -> Boolean): Int? {
    return collectExpandedPaths().singleOrNull { predicate(it.path.last()) }?.row
  }

  private fun translateRowPoint(row: Int, point: Point): Point = fixture.getRowPoint(row).apply { translate(point.x, point.y) }

  class PathNotFoundException(message: String? = null) : Exception(message) {
    constructor(path: List<String>) : this("$path not found")
  }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JTreeTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
@BeControlAdapter(JTreeFixtureAdapter::class)
interface JTreeFixtureRef : Component {
  fun clickRow(row: Int): JTreeFixtureRef
  fun clickPath(path: String): JTreeFixtureRef
  fun doubleClickRow(row: Int): JTreeFixtureRef
  fun doubleClickPath(path: String): JTreeFixtureRef
  fun rightClickRow(row: Int): JTreeFixtureRef
  fun rightClickPath(path: String): JTreeFixtureRef
  fun expandRow(row: Int): JTreeFixtureRef
  fun collapseRow(row: Int): JTreeFixtureRef
  fun expandPath(path: String): JTreeFixtureRef
  fun collapsePath(path: String): JTreeFixtureRef
  fun separator(): String
  fun valueAt(row: Int): String
  fun valueAt(path: String): String
  fun collectExpandedPaths(): TreePathToRowList
  fun collectSelectedPaths(): List<TreePath>
  fun selectRow(row: Int): JTreeFixtureRef?
  fun expandAll(timeoutMs: Int)
  fun getRowPoint(row: Int): Point
  fun replaceCellRendererReader(reader: CellRendererReader)
  fun getComponentAtRow(row: Int): Component
  fun collectIconsAtRow(row: Int): List<Icon>
  fun areTreeNodesLoaded(): Boolean
}

@Remote("javax.swing.JTree")
@BeControlClass(JTreeComponentClassBuilder::class)
interface JTreeComponent {
  fun expandRow(row: Int)
}

class JTreeComponentClassBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {
    return JTreeComponentBeControl(driver, frontendComponent, backendComponent)
  }
}

class JTreeComponentBeControl(
  driver: Driver,
  frontendComponent: Component,
  backendComponent: Component,
) : BeControlComponentBase(driver, frontendComponent, backendComponent), JTreeComponent {
  private val frontendJTreeComponent: JTreeComponent by lazy {
    driver.cast(onFrontend { byType(JTree::class.java) }.component, JTreeComponent::class)
  }

  override fun expandRow(row: Int) {
    return frontendJTreeComponent.expandRow(row)
  }
}
