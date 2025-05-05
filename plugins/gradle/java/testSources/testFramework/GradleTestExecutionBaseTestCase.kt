// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll.Companion.runAll
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.gradle.testFramework.util.testConsole
import org.jetbrains.plugins.gradle.testFramework.assertions.TestProxyAssertions
import org.jetbrains.plugins.gradle.testFramework.fixtures.SMTestRunnerOutputTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.SMTestRunnerOutputTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.util.consoleText
import org.jetbrains.plugins.gradle.testFramework.util.testProxyTree

abstract class GradleTestExecutionBaseTestCase : GradleExecutionTestCase() {

  private var _testRunnerOutputFixture: SMTestRunnerOutputTestFixture? = null
  val testRunnerOutputFixture: SMTestRunnerOutputTestFixture
    get() = requireNotNull(_testRunnerOutputFixture) {
      "Gradle test runner output fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  override fun setUp() {
    super.setUp()

    _testRunnerOutputFixture = SMTestRunnerOutputTestFixtureImpl(project)
    testRunnerOutputFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _testRunnerOutputFixture?.tearDown() },
      { _testRunnerOutputFixture = null },
      { super.tearDown() },
    )
  }

  fun assertTestViewTree(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    SimpleTreeAssertion.assertUnorderedTree(executionEnvironment.testConsole.testProxyTree, assert)
  }

  fun assertTestViewTreeIsEmpty() {
    assertTestViewTree {}
  }

  fun assertTestConsoleContains(expected: String) {
    Assertions.assertThat(executionEnvironment.testConsole.consoleText)
      .contains(expected)
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    Assertions.assertThat(executionEnvironment.testConsole.consoleText)
      .doesNotContain(expected)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleContains(expectedTextSample: String) {
    assertValue { testProxy ->
      Assertions.assertThat(testProxy.consoleText)
        .contains(expectedTextSample)
    }
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    assertValue { testProxy ->
      Assertions.assertThat(testProxy.consoleText)
        .doesNotContain(unexpectedTextSample)
    }
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