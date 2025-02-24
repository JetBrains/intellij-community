// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.testFramework.utils.vfs.deleteRecursively
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.testFramework.fixture.*
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import org.junit.jupiter.api.AfterEach

@GradleProjectTestApplication
abstract class GradleExecutionBaseTestCase : GradleProjectTestCase() {

  private var _executionFixture: GradleExecutionTestFixture? = null
  private val executionFixture: GradleExecutionTestFixture
    get() = requireNotNull(_executionFixture) {
      "Gradle execution fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  fun getExecutionEnvironment(): ExecutionEnvironment {
    return executionFixture.getExecutionEnvironment()
  }

  fun getTestExecutionConsole(): GradleTestsExecutionConsole {
    return executionFixture.getTestExecutionConsole()
  }

  override fun setUp() {
    super.setUp()

    cleanupProjectBuildDirectory()

    _executionFixture = GradleExecutionTestFixtureImpl(project, projectRoot)
    executionFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _executionFixture?.tearDown() },
      { _executionFixture = null },
      { cleanupProjectBuildDirectory() },
      { super.tearDown() },
    )
  }

  /**
   * Forces a project closing after each Gradle execution test.
   * The BuildViewTestFixture cannot release all editors in console view after the test.
   */
  @AfterEach
  fun destroyAllGradleFixturesAfterEachTest() {
    destroyAllGradleFixtures()
  }

  // '--rerun-tasks' corrupts gradle build caches fo gradle versions before 4.0 (included)
  private fun cleanupProjectBuildDirectory() {
    runWriteActionAndWait {
      projectRoot.deleteRecursively("build")
    }
  }

  override fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    super.test(gradleVersion, fixtureBuilder) {
      ExternalSystemExecutionTracer.printExecutionOutputOnException(test)
    }
  }

  fun executeTasks(commandLine: String, isRunAsTest: Boolean = false, isDebug: Boolean = false) {
    executionFixture.executeTasks(commandLine, isRunAsTest, isDebug)
  }

  suspend fun executeTasksAsync(commandLine: String, isRunAsTest: Boolean = false, isDebug: Boolean = false) {
    executionFixture.executeTasksAsync(commandLine, isRunAsTest, isDebug)
  }

  fun <R> waitForAnyGradleTaskExecution(action: () -> R): R {
    return executionFixture.waitForAnyGradleTaskExecution(action)
  }

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    executionFixture.assertSyncViewTree(assert)
  }

  fun assertBuildViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    executionFixture.assertBuildViewTree(assert)
  }

  fun assertRunViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    executionFixture.assertRunViewTree(assert)
  }

  fun assertRunViewTreeIsEmpty() {
    executionFixture.assertRunViewTreeIsEmpty()
  }

  fun assertTestViewTree(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    executionFixture.assertTestViewTree(assert)
  }

  fun assertTestViewTreeIsEmpty() {
    executionFixture.assertTestViewTreeIsEmpty()
  }

  fun assertTestConsoleContains(expected: String) {
    executionFixture.assertTestConsoleContains(expected)
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    executionFixture.assertTestConsoleDoesNotContain(expected)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleContains(expectedTextSample: String) {
    executionFixture.assertTestConsoleContains(this, expectedTextSample)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    executionFixture.assertTestConsoleDoesNotContain(this, unexpectedTextSample)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertPsiLocation(
    className: String, methodName: String? = null, parameterName: String? = null
  ) {
    executionFixture.assertPsiLocation(this, className, methodName, parameterName)
  }

  fun assertTestEventsContain(className: String, methodName: String? = null) {
    executionFixture.assertTestEventsContain(className, methodName)
  }

  fun assertTestEventsDoesNotContain(className: String, methodName: String? = null) {
    executionFixture.assertTestEventsDoNotContain(className, methodName)
  }

  fun assertTestEventsWasNotReceived() {
    executionFixture.assertTestEventsWereNotReceived()
  }

  fun assertTestEventCount(
    name: String,
    suiteStart: Int, suiteFinish: Int,
    testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int
  ) {
    executionFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }
}