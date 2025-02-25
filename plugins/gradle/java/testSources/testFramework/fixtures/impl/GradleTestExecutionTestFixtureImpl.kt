// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.testFramework.fixtures.SMTestRunnerOutputTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestExecutionViewTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionTestFixture

class GradleTestExecutionTestFixtureImpl(
  private val project: Project,
  private val executionFixture: GradleExecutionTestFixture,
) : GradleTestExecutionTestFixture {

  private lateinit var testRunnerOutputFixture: SMTestRunnerOutputTestFixture
  private lateinit var testExecutionViewFixture: GradleTestExecutionViewTestFixture

  override fun getTestExecutionConsole(): GradleTestsExecutionConsole {
    return testExecutionViewFixture.getTestExecutionConsole()
  }

  override fun setUp() {
    testRunnerOutputFixture = SMTestRunnerOutputTestFixtureImpl(project)
    testRunnerOutputFixture.setUp()

    testExecutionViewFixture = GradleTestExecutionViewTestFixtureImpl(executionFixture)
    testExecutionViewFixture.setUp()
  }

  override fun tearDown() {
    RunAll.runAll(
      { testExecutionViewFixture.tearDown() },
      { testRunnerOutputFixture.tearDown() },
    )
  }

  override fun assertTestViewTree(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    testExecutionViewFixture.assertTestTreeView(assert)
  }

  override fun assertTestViewTreeIsEmpty() {
    testExecutionViewFixture.assertTestTreeViewIsEmpty()
  }

  override fun assertTestConsoleContains(expectedTextSample: String) {
    testExecutionViewFixture.assertTestConsoleContains(expectedTextSample)
  }

  override fun assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    testExecutionViewFixture.assertTestConsoleDoesNotContain(unexpectedTextSample)
  }

  override fun assertTestConsoleContains(
    testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>,
    expectedTextSample: String,
  ) {
    testExecutionViewFixture.assertTestConsoleContains(testAssertion, expectedTextSample)
  }

  override fun assertTestConsoleDoesNotContain(
    testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>,
    unexpectedTextSample: String,
  ) {
    testExecutionViewFixture.assertTestConsoleDoesNotContain(testAssertion, unexpectedTextSample)
  }

  override fun assertTestEventCount(
    name: String,
    suiteStart: Int, suiteFinish: Int,
    testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int,
  ) {
    testRunnerOutputFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }
}