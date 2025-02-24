// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll.Companion.runAll
import org.jetbrains.plugins.gradle.testFramework.fixture.GradleTestExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixture.impl.GradleTestExecutionTestFixtureImpl

abstract class GradleTestExecutionBaseTestCase : GradleExecutionTestCase() {

  private var _testExecutionFixture: GradleTestExecutionTestFixture? = null
  val testExecutionFixture: GradleTestExecutionTestFixture
    get() = requireNotNull(_testExecutionFixture) {
      "Gradle test execution fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  override fun setUp() {
    super.setUp()

    _testExecutionFixture = GradleTestExecutionTestFixtureImpl(project, executionFixture)
    testExecutionFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _testExecutionFixture?.tearDown() },
      { _testExecutionFixture = null },
      { super.tearDown() },
    )
  }

  fun assertTestViewTree(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    testExecutionFixture.assertTestViewTree(assert)
  }

  fun assertTestViewTreeIsEmpty() {
    testExecutionFixture.assertTestViewTreeIsEmpty()
  }

  fun assertTestConsoleContains(expected: String) {
    testExecutionFixture.assertTestConsoleContains(expected)
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    testExecutionFixture.assertTestConsoleDoesNotContain(expected)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleContains(expectedTextSample: String) {
    testExecutionFixture.assertTestConsoleContains(this, expectedTextSample)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    testExecutionFixture.assertTestConsoleDoesNotContain(this, unexpectedTextSample)
  }

  fun assertTestEventCount(
    name: String,
    suiteStart: Int, suiteFinish: Int,
    testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int,
  ) {
    testExecutionFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }
}