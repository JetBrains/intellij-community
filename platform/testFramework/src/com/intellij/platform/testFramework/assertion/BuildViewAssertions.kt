// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildView
import com.intellij.build.ExecutionNode
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.tree.TreeUtil
import org.junit.jupiter.api.Assertions
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

object BuildViewAssertions {

  fun assertBuildViewTree(buildView: BuildView, assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    assertBuildViewTreeText(buildView) { treeString ->
      val actualTree = buildTree(treeString)
      SimpleTreeAssertion.assertTree(actualTree) {
        assertNode("", assert = assert)
      }
    }
  }

  fun assertBuildViewTreeText(buildView: BuildView, assert: (String) -> Unit) {
    assert(getBuildViewTreeText(buildView))
  }

  fun assertBuildViewNodeConsole(buildView: BuildView, nodeText: String, assert: (ExecutionConsole) -> Unit) {
    assert(getBuildViewNodeConsole(buildView, nodeText))
  }

  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: String, assert: (String) -> Unit) {
    assertBuildViewNodeConsole(buildView, nodeText) {
      assert((it as ConsoleViewImpl).text)
    }
  }

  fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeText: String) {
    val treeConsoleView = getBuildViewTreeConsoleView(buildView)
    val node = getTreeNode(treeConsoleView.tree, nodeText)
    val selectedNode = getTreeSelectedNode(treeConsoleView.tree)
    if (node != selectedNode) {
      Assertions.assertEquals(node.toString(), selectedNode.toString())
    }
  }

  private fun getBuildViewTreeText(buildView: BuildView): String {
    val eventView = getBuildViewTreeConsoleView(buildView)
    return getTreeStringPresentation(eventView.tree)
  }

  private fun getBuildViewNodeConsole(buildView: BuildView, nodeText: String): ExecutionConsole {
    val treeConsoleView = getBuildViewTreeConsoleView(buildView)
    selectTreeNode(treeConsoleView.tree, nodeText)
    val nodeConsole = runInEdtAndGet {
      treeConsoleView.selectedNodeConsole
    }
    Assertions.assertNotNull(nodeConsole) {
      "Cannot find console of the '$nodeText' node in tree:\n" +
      getTreeStringPresentation(treeConsoleView.tree) + "\n"
    }
    return nodeConsole!!
  }

  private fun getBuildViewTreeConsoleView(buildView: BuildView): BuildTreeConsoleView {
    val treeView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)!!
    treeView.addFilter { true }
    return treeView
  }

  private fun getTreeNode(tree: JTree, nodeText: String): TreeNode {
    val node = runInEdtAndGet {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)

      TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
        val userObject = it.userObject
        userObject is ExecutionNode && userObject.name == nodeText
      }
    }
    Assertions.assertNotNull(node) {
      "Cannot find the '$nodeText' node in tree:\n" +
      getTreeStringPresentation(tree) + "\n"
    }
    return node!!
  }

  private fun getTreeSelectedNode(tree: JTree): TreeNode {
    return tree.selectionPath!!.lastPathComponent!! as TreeNode
  }

  private fun getTreeStringPresentation(tree: JTree): String {
    return runInEdtAndGet {
      TreeUtil.expandAll(tree)

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)

      PlatformTestUtil.print(tree, false)
    }
  }

  private fun selectTreeNode(tree: JTree, nodeText: String) {
    val node = getTreeNode(tree, nodeText)
    runInEdtAndGet {
      TreeUtil.selectNode(tree, node)

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)
    }
  }
}