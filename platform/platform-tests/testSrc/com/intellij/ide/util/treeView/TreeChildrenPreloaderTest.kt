// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ComponentUtil.forceMarkAsShowing
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode

/**
 * The preloading of the children of the top-level nodes.
 *
 * The tree has no size in a test, so the preloading of the visible nodes requests nothing.
 * Each request comes from the preloading of the top-level nodes.
 */
@TestApplication
internal class TreeChildrenPreloaderTest {
  private val container = JPanel().also { forceMarkAsShowing(it, true) }
  private val requestedNodes = mutableListOf<Any>()
  private lateinit var model: DefaultTreeModelWithCachedPresentation
  private lateinit var root: DefaultMutableTreeNode
  private lateinit var tree: Tree

  @Test
  fun `preload the children of all top-level nodes`(): Unit = treeTest {
    model.setChildren(root, listOf(node("a"), leaf("file"), node("b"), node("c")))
    tree.isChildrenPreloadingEnabled = true
    awaitRequested("a", "b", "c")
    assertThat(requestedNodes).doesNotContain("file")
  }

  @Test
  fun `preload the children of a new top-level node`(): Unit = treeTest {
    model.setChildren(root, listOf(node("a")))
    tree.isChildrenPreloadingEnabled = true
    awaitRequested("a")
    model.insertChild(root, 1, node("b"))
    awaitRequested("b")
  }

  @Test
  fun `preload the children of the top-level nodes of a new model`(): Unit = treeTest {
    tree.isChildrenPreloadingEnabled = true
    val newRoot = DefaultMutableTreeNode("new root")
    val newModel = createModel(newRoot)
    newModel.setChildren(newRoot, listOf(node("a"), node("b")))
    tree.model = newModel
    awaitRequested("a", "b")
  }

  private fun treeTest(block: suspend () -> Unit) {
    timeoutRunBlocking {
      withForcedRespectIsShowingClientProperty {
        withContext(Dispatchers.EDT) {
          root = DefaultMutableTreeNode("root")
          model = createModel(root)
          tree = Tree(model)
          tree.isRootVisible = false
          container.add(JScrollPane(tree))
          try {
            block()
          }
          finally {
            tree.isChildrenPreloadingEnabled = false
            container.removeAll()
          }
        }
      }
    }
  }

  private fun createModel(root: DefaultMutableTreeNode): DefaultTreeModelWithCachedPresentation {
    val result = DefaultTreeModelWithCachedPresentation()
    result.childrenLoader = { requestedNodes += it.userObject }
    result.setRoot(root)
    return result
  }

  private suspend fun awaitRequested(vararg names: String) {
    waitUntil("the children of ${names.joinToString()} should have been requested") {
      requestedNodes.containsAll(names.toList())
    }
  }

  private fun node(name: String): DefaultMutableTreeNode = object : DefaultMutableTreeNode(name) {
    override fun isLeaf(): Boolean = false // the children are not loaded yet
  }

  private fun leaf(name: String): DefaultMutableTreeNode = DefaultMutableTreeNode(name)
}
