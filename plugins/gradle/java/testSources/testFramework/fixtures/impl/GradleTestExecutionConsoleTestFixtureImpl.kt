// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.build.BuildView
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestExecutionConsoleTestFixture
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions

class GradleTestExecutionConsoleTestFixtureImpl(
  private val executionEnvironmentFixture: GradleExecutionEnvironmentFixture,
) : GradleTestExecutionConsoleTestFixture {

  override fun setUp() = Unit

  override fun tearDown() = Unit

  override fun getTestExecutionConsole(): GradleTestsExecutionConsole {
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

  private fun getTestConsoleText(): String {
    val testExecutionConsole = getTestExecutionConsole()
    val rootProxy = testExecutionConsole.resultsViewer.root
    return getTestConsoleText(rootProxy)
  }

  private fun getTestConsoleText(testProxy: AbstractTestProxy): String {
    val printer = MockPrinter()
    printer.setShowHyperLink(true)
    testProxy.printOn(printer)
    return printer.allOut
  }

  override fun assertTestConsoleContains(expectedTextSample: String) {
    assertContains(expectedTextSample, getTestConsoleText())
  }

  override fun assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    assertDoesNotContain(unexpectedTextSample, getTestConsoleText())
  }

  override fun assertTestConsoleContains(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, expectedTextSample: String) {
    testAssertion.assertValue { testProxy ->
      assertContains(expectedTextSample, getTestConsoleText(testProxy))
    }
  }

  override fun assertTestConsoleDoesNotContain(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, unexpectedTextSample: String) {
    testAssertion.assertValue { testProxy ->
      assertDoesNotContain(unexpectedTextSample, getTestConsoleText(testProxy))
    }
  }

  override fun assertTestTreeView(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    val testExecutionConsole = getTestExecutionConsole()
    val resultsViewer = testExecutionConsole.resultsViewer
    val roots = resultsViewer.root.children
    val tree = buildTree(roots, { name }, { children })
    runReadAction { // all navigation tests requires read action
      SimpleTreeAssertion.assertUnorderedTree(tree, assert)
    }
  }

  override fun assertTestTreeViewIsEmpty() {
    assertTestTreeView {}
  }

  private fun assertContains(expectedTextSample: String, actualText: String) {
    val canonicalExpectedTextSample = StringUtil.convertLineSeparators(expectedTextSample)
    val canonicalActualText = StringUtil.convertLineSeparators(actualText)
    if (canonicalExpectedTextSample !in canonicalActualText) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Text doesn't contain text sample but should")
        .expected(canonicalExpectedTextSample)
        .actual(canonicalActualText)
        .build()
    }
  }

  private fun assertDoesNotContain(unexpectedTextSample: String, actualText: String) {
    val canonicalUnexpectedTextSample = StringUtil.convertLineSeparators(unexpectedTextSample)
    val canonicalActualText = StringUtil.convertLineSeparators(actualText)
    if (canonicalUnexpectedTextSample in canonicalActualText) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Text contains text sample but shouldn't")
        .expected(canonicalUnexpectedTextSample)
        .actual(canonicalActualText)
        .build()
    }
  }
}