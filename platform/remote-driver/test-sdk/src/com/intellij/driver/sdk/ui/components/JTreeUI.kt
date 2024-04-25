package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.model.TreePathToRowList
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import javax.swing.JTree
import kotlin.time.Duration.Companion.seconds


fun Finder.tree(@Language("xpath") xpath: String? = null) = x(xpath ?: Locators.byType(JTree::class.java),
                                                              JTreeUiComponent::class.java)

open class JTreeUiComponent(data: ComponentData) : UiComponent(data) {
  protected val fixture: JTreeFixtureRef
    get() = driver.new(JTreeFixtureRef::class, robotService.robot, component)

  fun clickRow(row: Int) = fixture.clickRow(row)
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

  fun expandAll(timeoutMs: Int) {
    fixture.expandAll(timeoutMs)
  }

  fun expandPath(vararg path: String, fullMatch: Boolean = true) = waitFor(10.seconds, errorMessage = "Failed find ${path.toList()}") {
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

  protected fun findExpandedPath(vararg path: String, fullMatch: Boolean): TreePathToRow? = findExpandedPaths(*path, fullMatch = fullMatch).singleOrNull()

  private fun findExpandedPaths(vararg path: String,
                                fullMatch: Boolean): List<TreePathToRow> = collectExpandedPaths().filter { expandedPath ->
    expandedPath.path.size == path.size && expandedPath.path.containsAllNodes(*path, fullMatch = fullMatch) ||
    expandedPath.path.size - 1 == path.size && expandedPath.path.drop(1).containsAllNodes(*path, fullMatch = fullMatch)
  }

  fun collectExpandedPaths(): List<TreePathToRow> {
    return fixture.collectExpandedPaths()
  }

  private fun List<String>.containsAllNodes(vararg treePath: String, fullMatch: Boolean): Boolean = zip(treePath).all {
    if (fullMatch) {
      it.first.equals(it.second, true)
    }
    else {
      it.first.contains(it.second, true)
    }
  }

  class PathNotFoundException(message: String? = null) : Exception(message) {
    constructor(path: List<String>) : this("$path not found")
  }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JTreeTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
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
  fun selectRow(row: Int): JTreeFixtureRef?
  fun expandAll(timeoutMs: Int)
}