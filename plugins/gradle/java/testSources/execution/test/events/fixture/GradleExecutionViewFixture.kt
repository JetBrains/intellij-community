// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events.fixture

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildView
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.testFramework.util.tree.Tree
import org.jetbrains.plugins.gradle.testFramework.util.tree.assertion.TreeAssertion
import org.jetbrains.plugins.gradle.testFramework.util.tree.buildTree
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions

class GradleExecutionViewFixture(
  private val executionEnvironmentFixture: GradleExecutionEnvironmentFixture
) : IdeaTestFixture {

  override fun setUp() = Unit

  override fun tearDown() = Unit

  fun getTestExecutionConsole(): GradleTestsExecutionConsole {
    val executionEnvironment = executionEnvironmentFixture.getExecutionEnvironment()
    val buildView = executionEnvironment.contentToReuse!!.executionConsole!! as BuildView
    val testExecutionConsole = buildView.consoleView as? GradleTestsExecutionConsole
    Assertions.assertNotNull(testExecutionConsole) {
      "Test view isn't shown"
    }
    val treeView = testExecutionConsole!!.resultsViewer.treeView!!
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
      TestConsoleProperties.HIDE_PASSED_TESTS.set(testExecutionConsole.properties, false)
      PlatformTestUtil.waitWhileBusy(treeView)
      PlatformTestUtil.expandAll(treeView)
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    return testExecutionConsole
  }

  private fun getSimplifiedRunTreeView(): Tree<Nothing?> {
    val executionEnvironment = executionEnvironmentFixture.getExecutionEnvironment()
    val buildView = executionEnvironment.contentToReuse!!.executionConsole!! as BuildView
    val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
    Assertions.assertNotNull(eventView) {
      "Run view isn't shown"
    }
    val treeView = eventView!!.tree!!
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
      eventView.addFilter { true }
      PlatformTestUtil.waitWhileBusy(treeView)
      PlatformTestUtil.expandAll(treeView)
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    val treeString = invokeAndWaitIfNeeded {
      PlatformTestUtil.print(treeView, false)
    }
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    return buildTree(treeString)
  }

  private fun getSimplifiedTestTreeView(): Tree<Nothing?> {
    val testExecutionConsole = getTestExecutionConsole()
    val treeView = testExecutionConsole.resultsViewer.treeView!!
    val treeString = invokeAndWaitIfNeeded {
      PlatformTestUtil.print(treeView, false)
    }
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    return buildTree(treeString)
  }

  private fun getTestConsoleText(): String {
    val testExecutionConsole = getTestExecutionConsole()
    val tree = testExecutionConsole.resultsViewer.treeView!!
    return invokeAndWaitIfNeeded {
      TreeUtil.selectFirstNode(tree)
      PlatformTestUtil.waitWhileBusy(tree)
      val console = testExecutionConsole.console as ConsoleViewImpl
      console.flushDeferredText()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      console.text
    }
  }

  fun assertTestConsoleContains(expected: String) {
    val actual = getTestConsoleText()
    if (expected !in actual) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Test execution console doesn't contain but should")
        .expected(expected)
        .actual(actual)
        .build()
    }
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    val actual = getTestConsoleText()
    if (expected in actual) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Test execution console contains but shouldn't")
        .expected(expected)
        .actual(actual)
        .build()
    }
  }

  fun assertRunTreeView(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    TreeAssertion.assertTree(getSimplifiedRunTreeView()) {
      assertNode("", assert)
    }
  }

  fun assertTestTreeView(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    TreeAssertion.assertTree(getSimplifiedTestTreeView(), isUnordered = true) {
      assertNode("[root]", assert)
    }
  }

  fun assertRunTreeViewIsEmpty() {
    assertRunTreeView {}
  }

  fun assertTestTreeViewIsEmpty() {
    assertTestTreeView {}
  }

  fun assertSMTestProxyTree(assert: TreeAssertion<AbstractTestProxy>.() -> Unit) {
    val testExecutionConsole = getTestExecutionConsole()
    val resultsViewer = testExecutionConsole.resultsViewer
    val roots = resultsViewer.root.children
    val tree = buildTree(roots, { name }, { children })
    runReadAction { // all navigation tests requires read action
      TreeAssertion.assertTree(tree, isUnordered = true, assert)
    }
  }
}