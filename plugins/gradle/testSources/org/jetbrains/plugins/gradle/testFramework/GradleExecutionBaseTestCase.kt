// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.utils.vfs.deleteRecursively
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionConsoleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionOutputFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleExecutionTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleExecutionConsoleFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleExecutionEnvironmentFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleExecutionOutputFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import org.junit.jupiter.api.AfterEach

@GradleProjectTestApplication
abstract class GradleExecutionBaseTestCase : GradleProjectTestCase() {

  private var _executionOutputFixture: GradleExecutionOutputFixture? = null
  val executionOutputFixture: GradleExecutionOutputFixture
    get() = requireNotNull(_executionOutputFixture) {
      "Gradle execution output fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _executionEnvironmentFixture: GradleExecutionEnvironmentFixture? = null
  val executionEnvironmentFixture: GradleExecutionEnvironmentFixture
    get() = requireNotNull(_executionEnvironmentFixture) {
      "Gradle execution environment fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _executionConsoleFixture: GradleExecutionConsoleFixture? = null
  val executionConsoleFixture: GradleExecutionConsoleFixture
    get() = requireNotNull(_executionConsoleFixture) {
      "Gradle execution console fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _buildViewFixture: BuildViewTestFixture? = null
  val buildViewFixture: BuildViewTestFixture
    get() = requireNotNull(_buildViewFixture) {
      "Gradle execution build view fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _executionFixture: GradleExecutionTestFixture? = null
  val executionFixture: GradleExecutionTestFixture
    get() = requireNotNull(_executionFixture) {
      "Gradle execution fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  override fun setUp() {
    super.setUp()

    cleanupProjectBuildDirectory()

    _executionOutputFixture = GradleExecutionOutputFixtureImpl()
    executionOutputFixture.setUp()

    _executionEnvironmentFixture = GradleExecutionEnvironmentFixtureImpl(project)
    executionEnvironmentFixture.setUp()

    _executionConsoleFixture = GradleExecutionConsoleFixtureImpl(project, executionEnvironmentFixture)
    executionConsoleFixture.setUp()

    _buildViewFixture = BuildViewTestFixture(project)
    buildViewFixture.setUp()

    _executionFixture = GradleExecutionTestFixtureImpl(project, projectRoot)
    executionFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { _executionFixture?.tearDown() },
      { _executionFixture = null },
      { _buildViewFixture?.tearDown() },
      { _buildViewFixture = null },
      { _executionConsoleFixture?.tearDown() },
      { _executionConsoleFixture = null },
      { _executionEnvironmentFixture?.tearDown() },
      { _executionEnvironmentFixture = null },
      { _executionOutputFixture?.tearDown() },
      { _executionOutputFixture = null },
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

  fun <R> waitForAnyGradleTaskExecution(action: () -> R): R {
    return executionFixture.waitForAnyGradleTaskExecution(action)
  }

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertSyncViewTree(assert)
  }

  fun assertSyncViewNode(nodeText: String, assert: (String) -> Unit) {
    buildViewFixture.assertSyncViewNode(nodeText, assert)
  }

  fun assertSyncViewSelectedNode(nodeText: String, assert: (String) -> Unit) {
    buildViewFixture.assertSyncViewSelectedNode(nodeText, assert)
  }

  fun assertBuildViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertBuildViewTree(assert)
  }

  fun assertRunViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    executionConsoleFixture.assertRunTreeView(assert)
  }

  fun SimpleTreeAssertion.Node<AbstractTestProxy>.assertPsiLocation(
    className: String, methodName: String? = null, parameterName: String? = null
  ) {
    executionConsoleFixture.assertPsiLocation(this, className, methodName, parameterName)
  }

  fun assertTestEventsContain(className: String, methodName: String? = null) {
    executionOutputFixture.assertTestEventContain(className, methodName)
  }

  fun assertTestEventsDoesNotContain(className: String, methodName: String? = null) {
    executionOutputFixture.assertTestEventDoesNotContain(className, methodName)
  }

  fun assertTestEventsWasNotReceived() {
    executionOutputFixture.assertTestEventsWasNotReceived()
  }
}