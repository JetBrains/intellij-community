// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultTreeModel

/**
 * A restore of the tree state must not expand the only child of a restored node, when the user collapsed that child.
 */
@TestApplication
internal class ChangesTreeStateRestoreTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `the collapsed only child stays collapsed with ALWAYS_KEEP`(): Unit = checkOnlyChildStaysCollapsed(ChangesTree.ALWAYS_KEEP)

  @Test
  fun `the collapsed only child stays collapsed with KEEP_NON_EMPTY`(): Unit = checkOnlyChildStaysCollapsed(ChangesTree.KEEP_NON_EMPTY)

  @Test
  fun `an expand by the user still expands the only child`(): Unit = timeoutRunBlocking {
    val actual = withContext(Dispatchers.EDT) {
      val tree = TestTree(projectFixture.get(), ChangesTree.ALWAYS_KEEP)
      tree.replaceModel()
      tree.collapseRow(0)
      tree.expandRow(0)
      tree.rows()
    }
    assertEquals(listOf("/repo:true", "/repo/module:true", "/repo/module/a:false", "/repo/module/b:false"), actual)
  }

  private fun checkOnlyChildStaysCollapsed(strategy: ChangesTree.TreeStateStrategy<*>): Unit = timeoutRunBlocking {
    val (expected, actual) = withContext(Dispatchers.EDT) {
      val tree = TestTree(projectFixture.get(), strategy)
      tree.replaceModel()
      tree.expandRow(0)
      tree.collapseRow(1)
      val before = tree.rows()
      tree.replaceModel()
      before to tree.rows()
    }
    assertEquals(listOf("/repo:true", "/repo/module:false"), expected)
    assertEquals(expected, actual)
  }

  private class TestTree(project: Project, strategy: TreeStateStrategy<*>) : ChangesTree(project, false, false) {
    init {
      treeStateStrategy = strategy
    }

    override fun rebuildTree() {}

    /**
     * Sets a new model with new nodes: "/repo" is the only top-level node, and "/repo/module" is its only child.
     */
    fun replaceModel() {
      val root = ChangesBrowserNode.createRoot()
      val repo = node("/repo", true)
      val module = node("/repo/module", true)
      for (name in listOf("a", "b")) {
        val directory = node("/repo/module/$name", true)
        directory.add(node("/repo/module/$name/file.txt", false))
        module.add(directory)
      }
      repo.add(module)
      root.add(repo)
      updateTreeModel(DefaultTreeModel(root))
    }

    fun rows(): List<String> = (0 until rowCount).map { row ->
      "${(getPathForRow(row).lastPathComponent as ChangesBrowserNode<*>).userObject}:${isExpanded(row)}"
    }

    private fun node(path: String, directory: Boolean) = object : ChangesBrowserNode<String>(path) {
      override fun getTextPresentation(): String = path
      override fun isDirectory(): Boolean = directory
      override fun isFile(): Boolean = !directory
    }
  }
}
