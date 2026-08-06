// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildView
import com.intellij.build.ExecutionNode
import com.intellij.build.SUCCESSFUL_STEPS_FILTER
import com.intellij.build.WARNINGS_FILTER
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.unwrapDelegate
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTree
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion.NodeMatcher
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTreePathTree
import com.intellij.platform.testFramework.assertion.treeAssertion.node
import com.intellij.platform.testFramework.assertion.treeAssertion.getTreeString
import com.intellij.platform.testFramework.assertion.treeAssertion.isSelected
import com.intellij.platform.testFramework.assertion.treeAssertion.mapTreeValues
import com.intellij.platform.testFramework.assertion.treeAssertion.userObject
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import javax.swing.tree.TreePath

typealias BuildViewNodeAssertion = SimpleTreeAssertion.Node<BuildViewNodeContent>

interface BuildViewNodeContent {

  val treeConsoleView: BuildTreeConsoleView

  val treePath: TreePath
}

val BuildViewNodeContent.isNodeSelected: Boolean
  get() = treeConsoleView.tree.isSelected(treePath)

val BuildViewNodeContent.isNodeExpanded: Boolean
  get() = treeConsoleView.tree.isExpanded(treePath)

val BuildViewNodeContent.executionNode: ExecutionNode
  get() = treeConsoleView.findNode(treePath.userObject)
          ?: throw AssertionError("Cannot find ExecutionNode by TreePath: $treePath")

val BuildViewNodeContent.consoleView: ExecutionConsole
  get() = treeConsoleView.resolveNodeConsole(executionNode)

val BuildViewNodeContent.consoleText: String
  get() = (consoleView.unwrapDelegate() as ConsoleViewImpl).text

fun BuildViewNodeAssertion.assertIsNodeSelected(expected: Boolean) {
  assertValue {
    assertThat(it.isNodeSelected)
      .describedAs { "Node select assertion for TreePath: ${it.treePath}\n" +
                     " JTree.selectedPath=${it.treeConsoleView.tree.selectionPath}\n}" }
      .isEqualTo(expected)
  }
}

fun BuildViewNodeAssertion.assertIsNodeExpanded(expected: Boolean) {
  assertValue {
    assertThat(it.isNodeExpanded)
      .describedAs { "Node expand assertion for TreePath: ${it.treePath}" }
      .isEqualTo(expected)
  }
}

fun BuildViewNodeAssertion.assertConsoleText(expectedConsoleText: String): Unit =
  assertValue {
    assertThat(it.consoleText)
      .describedAs { "Console text assertion for TreePath: ${it.treePath}" }
      .isEqualTo(expectedConsoleText)
  }

@Suppress("DeprecatedCallableAddReplaceWith")
object BuildViewAssertions {

  val BuildView.treeConsoleView: BuildTreeConsoleView
    get() = eventView ?: throw AssertionError("BuildView tree console is not setup")

  fun assertBuildViewTree(buildView: BuildView, assert: BuildViewNodeAssertion.() -> Unit): Unit =
    assertBuildViewTree(buildView.treeConsoleView, assert = assert)

  fun assertBuildViewNode(buildView: BuildView, nodeText: String, assert: (BuildViewNodeContent) -> Unit): Unit =
    assertBuildViewNode(buildView.treeConsoleView, nodeText, assert = assert)

  fun assertBuildViewNode(buildView: BuildView, nodeText: Regex, assert: (BuildViewNodeContent) -> Unit): Unit =
    assertBuildViewNode(buildView.treeConsoleView, nodeText, assert = assert)

  @RequiresEdt
  private fun buildBuildViewTree(treeConsoleView: BuildTreeConsoleView): SimpleTree<BuildViewNodeContent> {

    treeConsoleView.addFilter(SUCCESSFUL_STEPS_FILTER)
    treeConsoleView.addFilter(WARNINGS_FILTER)

    return buildTreePathTree(treeConsoleView.tree)
      .mapTreeValues {
        object : BuildViewNodeContent {
          override val treeConsoleView = treeConsoleView
          override val treePath = it.value
        }
      }
  }

  @RequiresEdt
  private fun getBuildViewNode(
    treeConsoleView: BuildTreeConsoleView,
    nodeMatcher: NodeMatcher<BuildViewNodeContent>,
  ): BuildViewNodeContent {
    return buildBuildViewTree(treeConsoleView)
      .node(nodeMatcher)
      .value
  }

  fun assertBuildViewTree(
    treeConsoleView: BuildTreeConsoleView,
    assert: BuildViewNodeAssertion.() -> Unit,
  ) {
    waitUntilAssertSucceedsBlocking {
      invokeAndWaitIfNeeded {
        val tree = buildBuildViewTree(treeConsoleView)
        SimpleTreeAssertion.assertTree(tree) {
          assertNode("", assert = assert)
        }
      }
    }
  }

  fun assertBuildViewNode(
    treeConsoleView: BuildTreeConsoleView,
    nodeText: String,
    assert: (BuildViewNodeContent) -> Unit,
  ) {
    assertBuildViewNode(treeConsoleView, NodeMatcher.name(nodeText), assert)
  }

  fun assertBuildViewNode(
    treeConsoleView: BuildTreeConsoleView,
    nodeText: Regex,
    assert: (BuildViewNodeContent) -> Unit,
  ) {
    assertBuildViewNode(treeConsoleView, NodeMatcher.regex(nodeText), assert)
  }

  fun assertBuildViewNode(
    treeConsoleView: BuildTreeConsoleView,
    nodeMatcher: NodeMatcher<BuildViewNodeContent>,
    assert: (BuildViewNodeContent) -> Unit,
  ) {
    waitUntilAssertSucceedsBlocking {
      invokeAndWaitIfNeeded {
        assert(getBuildViewNode(treeConsoleView, nodeMatcher))
      }
    }
  }

  @Deprecated("Use assertBuildViewTree instead")
  fun assertBuildViewTreeText(buildView: BuildView, assert: (String) -> Unit) {
    waitUntilAssertSucceedsBlocking {
      invokeAndWaitIfNeeded {
        val tree = buildBuildViewTree(buildView.treeConsoleView)
        assert(tree.getTreeString())
      }
    }
  }

  @Deprecated("Use assertBuildViewTree instead")
  fun assertBuildViewTreeText(buildView: BuildView, executionTree: String) {
    assertBuildViewTreeText(buildView) {
      Assertions.assertEquals(executionTree.trim(), it.trim())
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeConsole(buildView: BuildView, nodeText: String, assert: (ExecutionConsole) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      assert(it.consoleView)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: String, consoleText: String) {
    assertBuildViewNodeConsoleText(buildView, nodeText) {
      Assertions.assertEquals(consoleText, it)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: String, assert: (String) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      assert(it.consoleText)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeConsoleText(buildView: BuildView, nodeText: Regex, assert: (String) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      assert(it.consoleText)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeText: String) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewNodeIsSelected(buildView: BuildView, nodeText: Regex) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewSelectedNodeConsoleText(buildView: BuildView, nodeText: String, consoleText: String) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
      Assertions.assertEquals(consoleText, it.consoleText)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewSelectedNodeConsoleText(buildView: BuildView, nodeText: String, assert: (String) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
      assert(it.consoleText)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewSelectedNodeConsoleText(buildView: BuildView, nodeText: Regex, assert: (String) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
      assert(it.consoleText)
    }
  }

  @Deprecated("Use assertBuildViewNode instead")
  fun assertBuildViewSelectedNodeConsole(buildView: BuildView, nodeText: String, assert: (ExecutionConsole) -> Unit) {
    assertBuildViewNode(buildView, nodeText) {
      Assertions.assertTrue(it.isNodeSelected)
      assert(it.consoleView)
    }
  }

  @Deprecated("Use BuildTreeConsoleView.addFilter directly")
  fun showAllNodes(treeView: BuildTreeConsoleView) {
    treeView.addFilter(SUCCESSFUL_STEPS_FILTER)
    treeView.addFilter(WARNINGS_FILTER)
  }
}