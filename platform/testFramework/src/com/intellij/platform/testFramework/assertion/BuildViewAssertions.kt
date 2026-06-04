// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildView
import com.intellij.build.SUCCESSFUL_STEPS_FILTER
import com.intellij.build.WARNINGS_FILTER
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.unwrapDelegate
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.tree.TreeUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
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
    waitUntilAssertSucceedsBlocking {
      assert(getBuildViewTreeText(buildView))
    }
  }

  fun assertBuildViewNodeConsole(buildView: BuildView, nodeText: String, assert: (ExecutionConsole) -> Unit): Unit =
    assertBuildViewNodeConsole(buildView, BuildViewNodeMatcher.exactly(nodeText), assert)

  private fun assertBuildViewNodeConsole(buildView: BuildView, nodeMatcher: BuildViewNodeMatcher, assert: (ExecutionConsole) -> Unit) {
    val treeConsoleView = getBuildViewTreeConsoleView(buildView)
    selectTreeNode(treeConsoleView.tree, nodeMatcher)
    waitUntilAssertSucceedsBlocking {
      val nodeConsole = runInEdtAndGet {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        treeConsoleView.selectedNodeConsole
      }
      assertNotNull(nodeConsole) {
        "Cannot find console of the '$nodeMatcher' node in tree:\n" +
        getTreeStringPresentation(treeConsoleView.tree) + "\n"
      }
      assert(nodeConsole!!)
    }
  }

  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: String, assert: (String) -> Unit): Unit =
    assertBuildViewNodeConsoleText(buildView, BuildViewNodeMatcher.exactly(nodeText), assert)

  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: Regex, assert: (String) -> Unit): Unit =
    assertBuildViewNodeConsoleText(buildView, BuildViewNodeMatcher.regex(nodeText), assert)

  private fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeMatcher: BuildViewNodeMatcher, assert: (String) -> Unit) {
    assertBuildViewNodeConsole(buildView, nodeMatcher) {
      assert((it.unwrapDelegate() as ConsoleViewImpl).text)
    }
  }

  fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeText: String): Unit =
    assertBuildViewNodeIsSelected(buildView, BuildViewNodeMatcher.exactly(nodeText))

  fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeText: Regex): Unit =
    assertBuildViewNodeIsSelected(buildView, BuildViewNodeMatcher.regex(nodeText))

  private fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeMatcher: BuildViewNodeMatcher) {
    val treeConsoleView = getBuildViewTreeConsoleView(buildView)
    val node = getTreeNode(treeConsoleView.tree, nodeMatcher)
    val selectedNode = getTreeSelectedNode(treeConsoleView.tree)
    if (node != selectedNode) {
      Assertions.assertEquals(node.toString(), selectedNode.toString())
    }
  }

  private fun getBuildViewTreeText(buildView: BuildView): String {
    val eventView = getBuildViewTreeConsoleView(buildView)
    return getTreeStringPresentation(eventView.tree)
  }

  fun showAllNodes(treeView: BuildTreeConsoleView) {
    treeView.addFilter(SUCCESSFUL_STEPS_FILTER)
    treeView.addFilter(WARNINGS_FILTER)
  }

  private fun getBuildViewTreeConsoleView(buildView: BuildView): BuildTreeConsoleView {
    val treeView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)!!
    showAllNodes(treeView)
    return treeView
  }

  private fun getTreeNode(tree: JTree, nodeMatcher: BuildViewNodeMatcher): TreeNode {
    return waitUntilAssertSucceedsBlocking retry@{
      val node = getTreeNodeOrNull(tree, nodeMatcher)
      assertNotNull(node) {
        "Cannot find the '$nodeMatcher' node in tree:\n" +
        getTreeStringPresentation(tree) + "\n"
      }
      return@retry node!!
    }
  }

  private fun getTreeNodeOrNull(tree: JTree, nodeMatcher: BuildViewNodeMatcher): DefaultMutableTreeNode? = runInEdtAndGet {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    PlatformTestUtil.waitWhileBusy(tree)

    TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
      nodeMatcher.matchesNodeText(it.userObject.toString())
    }
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

  private fun selectTreeNode(tree: JTree, nodeMatcher: BuildViewNodeMatcher) {
    val node = getTreeNode(tree, nodeMatcher)
    runInEdtAndGet {
      TreeUtil.selectNode(tree, node)

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)
    }
  }

  private interface BuildViewNodeMatcher {

    fun matchesNodeText(nodeText: String): Boolean

    override fun toString(): String

    companion object {

      fun exactly(expectedNodeText: String): BuildViewNodeMatcher = object : BuildViewNodeMatcher {
        override fun matchesNodeText(nodeText: String): Boolean = nodeText == expectedNodeText
        override fun toString(): String = expectedNodeText
      }

      fun regex(expectedNodeText: Regex): BuildViewNodeMatcher = object : BuildViewNodeMatcher {
        override fun matchesNodeText(nodeText: String): Boolean = expectedNodeText.matches(nodeText)
        override fun toString(): String = expectedNodeText.toString()
      }
    }
  }
}