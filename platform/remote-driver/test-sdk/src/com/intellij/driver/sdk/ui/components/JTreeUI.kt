package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.driver.sdk.remoteDev.BeControlAdapter
import com.intellij.driver.sdk.remoteDev.JTreeFixtureAdapter
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import java.awt.Point
import javax.swing.JTree
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


fun Finder.tree(@Language("xpath") xpath: String? = null) = x(xpath ?: xQuery { byType(JTree::class.java) },
                                                              JTreeUiComponent::class.java)

open class JTreeUiComponent(data: ComponentData) : UiComponent(data) {
  val fixture: JTreeFixtureRef
    get() = driver.new(JTreeFixtureRef::class, robot, component)

  fun clickRow(row: Int) = fixture.clickRow(row)
  fun clickRow(predicate: (String) -> Boolean) {
    collectExpandedPaths().singleOrNull { predicate(it.path.last()) }?.let {
      clickRow(it.row)
    } ?: throw PathNotFoundException("row not found")
  }
  fun rightClickRow(row: Int) = fixture.rightClickRow(row)
  fun doubleClickRow(row: Int) = fixture.doubleClickRow(row)
  fun clickPath(vararg path: String, fullMatch: Boolean = true) {
    expandPath(*path, fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      clickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  fun rightClickPath(vararg path: String, fullMatch: Boolean = true) {
    expandPath(*path, fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      rightClickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  fun doubleClickPath(vararg path: String, fullMatch: Boolean = true) {
    expandPath(*path, fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      doubleClickRow(it.row)
    } ?: throw PathNotFoundException(path.toList())
  }

  private fun selectPathWithEnter(vararg path: String, fullMatch: Boolean = true) {
    expandPath(*path, fullMatch = fullMatch)
    findExpandedPath(*path, fullMatch = fullMatch)?.let {
      clickRow(it.row)
      keyboard { enter() }
    } ?: throw PathNotFoundException(path.toList())
  }

  fun expandAll(timeout: Duration) {
    fixture.expandAll(timeout.inWholeMilliseconds.toInt())
  }

  fun expandPath(vararg path: String, fullMatch: Boolean = true) = waitFor("Expand path '${path.toList()}'", 10.seconds) {
    try {
      val expandedPath = mutableListOf<String>()
      path.forEach {
        var currentPathPaths = findExpandedPaths(*(expandedPath + listOf(it)).toTypedArray(), fullMatch = fullMatch)
        if (currentPathPaths.isEmpty()) {
          doubleClickPath(*expandedPath.toTypedArray(), fullMatch = fullMatch)
          currentPathPaths = findExpandedPaths(*(expandedPath + listOf(it)).toTypedArray(), fullMatch = fullMatch)
        }
        if (currentPathPaths.isEmpty()) {
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

  private fun findExpandedPaths(vararg path: String,
                                fullMatch: Boolean): List<TreePathToRow> = collectExpandedPaths().filter { expandedPath ->
    expandedPath.path.size == path.size && expandedPath.path.containsAllNodes(*path, fullMatch = fullMatch) ||
    expandedPath.path.size - 1 == path.size && expandedPath.path.drop(1).containsAllNodes(*path, fullMatch = fullMatch)
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

  fun pathExists(vararg path: String): Boolean{
    return try {
      clickPath(*path, fullMatch = false)
      true
    }
    catch (notFound: PathNotFoundException) {
      false
    }
  }

  fun clickRowWithShift(row: Int, shift: Point = Point(0, 0)) {
    click(fixture.getRowPoint(row).apply { translate(shift.x, shift.y) })
  }

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
}