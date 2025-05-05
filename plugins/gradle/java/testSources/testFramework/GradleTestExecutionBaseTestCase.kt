// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll.Companion.runAll
import org.jetbrains.plugins.gradle.testFramework.assertions.TestProxyAssertions
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestExecutionConsoleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.SMTestRunnerOutputTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestExecutionConsoleTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.SMTestRunnerOutputTestFixtureImpl

abstract class GradleTestExecutionBaseTestCase : GradleExecutionTestCase() {

  private var _testExecutionConsoleFixture: GradleTestExecutionConsoleTestFixture? = null
  val testExecutionConsoleFixture: GradleTestExecutionConsoleTestFixture
    get() = requireNotNull(_testExecutionConsoleFixture) {
      "Gradle test execution console fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _testRunnerOutputFixture: SMTestRunnerOutputTestFixture? = null
  val testRunnerOutputFixture: SMTestRunnerOutputTestFixture
    get() = requireNotNull(_testRunnerOutputFixture) {
      "Gradle test runner output fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  override fun setUp() {
    super.setUp()

    _testExecutionConsoleFixture = GradleTestExecutionConsoleTestFixtureImpl(executionEnvironmentFixture)
    testExecutionConsoleFixture.setUp()

    _testRunnerOutputFixture = SMTestRunnerOutputTestFixtureImpl(project)
    testRunnerOutputFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _testRunnerOutputFixture?.tearDown() },
      { _testRunnerOutputFixture = null },
      { _testExecutionConsoleFixture?.tearDown() },
      { _testExecutionConsoleFixture = null },
      { super.tearDown() },
    )
  }

  fun assertTestViewTree(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    testExecutionConsoleFixture.assertTestTreeView(assert)
  }

  fun assertTestViewTreeIsEmpty() {
    testExecutionConsoleFixture.assertTestTreeViewIsEmpty()
  }

  fun assertTestConsoleContains(expected: String) {
    testExecutionConsoleFixture.assertTestConsoleContains(expected)
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    testExecutionConsoleFixture.assertTestConsoleDoesNotContain(expected)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleContains(expectedTextSample: String) {
    testExecutionConsoleFixture.assertTestConsoleContains(this, expectedTextSample)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    testExecutionConsoleFixture.assertTestConsoleDoesNotContain(this, unexpectedTextSample)
  }

  fun assertTestEventCount(
    name: String,
    suiteStart: Int, suiteFinish: Int,
    testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int,
  ) {
    testRunnerOutputFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertPsiLocation(
    className: String, methodName: String? = null, parameterName: String? = null
  ) {
    assertValue { testProxy ->
      TestProxyAssertions.assertPsiLocation(project, testProxy, className, methodName, parameterName)
    }
  }
}