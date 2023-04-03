// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.LocalTimeCounter
import org.jetbrains.plugins.gradle.execution.test.events.fixture.TestExecutionConsoleEventFixture
import org.jetbrains.plugins.gradle.execution.test.events.fixture.TestExecutionConsoleFixture
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isSupportedJUnit5
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.util.tree.TreeAssertion
import org.jetbrains.plugins.gradle.testFramework.util.tree.buildTree
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.waitForTaskExecution
import org.junit.jupiter.api.Assertions

abstract class GradleTestExecutionTestCase : GradleProjectTestCase() {

  private lateinit var testDisposable: Disposable
  private lateinit var buildViewTestFixture: BuildViewTestFixture

  lateinit var testExecutionConsoleFixture: TestExecutionConsoleFixture
  lateinit var testExecutionEventFixture: TestExecutionConsoleEventFixture

  override fun setUp() {
    super.setUp()

    testDisposable = Disposer.newDisposable()

    testExecutionConsoleFixture = TestExecutionConsoleFixture()
    testExecutionConsoleFixture.setUp()

    testExecutionEventFixture = TestExecutionConsoleEventFixture(project)
    testExecutionEventFixture.setUp()

    buildViewTestFixture = BuildViewTestFixture(project)
    buildViewTestFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { testExecutionConsoleFixture.tearDown() },
      { testExecutionEventFixture.tearDown() },
      { buildViewTestFixture.tearDown() },
      { Disposer.dispose(testDisposable) },
      { super.tearDown() },
    )
  }

  val jUnitTestAnnotationClass: String
    get() = when (isSupportedJunit5()) {
      true -> "org.junit.jupiter.api.Test"
      else -> "org.junit.Test"
    }

  val jUnitIgnoreAnnotationClass: String
    get() = when (isSupportedJunit5()) {
      true -> "org.junit.jupiter.api.Disabled"
      else -> "org.junit.Ignore"
    }

  /**
   * Call this method inside [setUp] to print events trace to console
   */
  @Suppress("unused")
  fun initTextNotificationEventsPrinter() {
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        when (stdOut) {
          true -> System.out.print(text)
          else -> System.err.print(text)
        }
      }
    }, testDisposable)
  }

  fun executeTasks(commandLine: String) {
    val runManager = RunManager.getInstance(project)
    val runConfigurationName = "GradleTestExecutionTestCase (" + LocalTimeCounter.currentTime() + ")"
    val runnerSettings = runManager.createConfiguration(runConfigurationName, GradleExternalTaskConfigurationType::class.java)
    val runConfiguration = runnerSettings.configuration as GradleRunConfiguration
    runConfiguration.rawCommandLine = commandLine
    runConfiguration.isForceTestExecution = true
    runConfiguration.settings.externalProjectPath = projectPath
    runConfiguration.settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    val executorId = DefaultRunExecutor.EXECUTOR_ID
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)!!
    val runner = ProgramRunner.getRunner(executorId, runConfiguration)!!
    Assertions.assertEquals(ExternalSystemConstants.RUNNER_ID, runner.runnerId)
    val environment = ExecutionEnvironment(executor, runner, runnerSettings, project)
    waitForTaskExecution {
      runWriteActionAndWait {
        runner.execute(environment)
      }
    }
  }

  fun isSupportedTestLauncher(): Boolean {
    return isGradleAtLeast("7.6")
  }

  private fun isSupportedJunit5(): Boolean {
    return isSupportedJUnit5(gradleVersion)
  }

  private fun assertFullBuildExecutionTree(assert: TreeAssertion<Nothing?>.() -> Unit) {
    buildViewTestFixture.assertBuildViewTreeEquals { treeString ->
      val tree = buildTree(treeString!!)
      TreeAssertion.assertTree(tree, assert)
    }
  }

  private fun assertFullBuildExecutionTreeContains(assert: TreeAssertion<Nothing?>.() -> Unit) {
    buildViewTestFixture.assertBuildViewTreeEquals { treeString ->
      val tree = buildTree(treeString!!)
      TreeAssertion.assertMatchesTree(tree, assert)
    }
  }

  fun assertBuildExecutionTree(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    assertFullBuildExecutionTree { assertNode("", assert) }
  }

  fun assertBuildExecutionTreeContains(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    assertFullBuildExecutionTreeContains { assertNode("", assert) }
  }

  fun assertTestExecutionConsoleContains(expected: String) {
    testExecutionConsoleFixture.assertTestExecutionConsoleContains(expected)
  }

  fun assertTestExecutionTree(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    testExecutionConsoleFixture.assertTestExecutionTree(assert)
  }

  fun assertTestExecutionTreeIsEmpty() {
    testExecutionConsoleFixture.assertTestExecutionTreeIsEmpty()
  }

  fun assertTestExecutionTreeIsNotCreated() {
    testExecutionConsoleFixture.assertTestExecutionTreeIsNotCreated()
  }

  fun assertSMTestProxyTree(assert: TreeAssertion<AbstractTestProxy>.() -> Unit) {
    testExecutionConsoleFixture.assertSMTestProxyTree(assert)
  }

  fun assertTestEventCount(
    name: String, suiteStart: Int, suiteFinish: Int, testStart: Int, testFinish: Int, testFailure: Int, testIgnore: Int
  ) {
    testExecutionEventFixture.assertTestEventCount(name, suiteStart, suiteFinish, testStart, testFinish, testFailure, testIgnore)
  }
}