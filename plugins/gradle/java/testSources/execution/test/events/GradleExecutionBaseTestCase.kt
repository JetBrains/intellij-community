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
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.LocalTimeCounter
import org.jetbrains.plugins.gradle.execution.test.events.fixture.GradleExecutionEnvironmentFixture
import org.jetbrains.plugins.gradle.execution.test.events.fixture.GradleExecutionViewFixture
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.util.tree.assertion.TreeAssertion
import org.jetbrains.plugins.gradle.testFramework.util.tree.buildTree
import org.jetbrains.plugins.gradle.testFramework.util.waitForAnyExecution
import org.jetbrains.plugins.gradle.testFramework.util.waitForGradleEventDispatcherClosing
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions

abstract class GradleExecutionBaseTestCase : GradleProjectTestCase() {

  private lateinit var testDisposable: Disposable

  lateinit var executionEnvironmentFixture: GradleExecutionEnvironmentFixture
  lateinit var executionConsoleFixture: GradleExecutionViewFixture
  private lateinit var buildViewFixture: BuildViewTestFixture

  override fun setUp() {
    super.setUp()

    cleanupProjectBuildDirectory()

    testDisposable = Disposer.newDisposable()

    executionEnvironmentFixture = GradleExecutionEnvironmentFixture(project)
    executionEnvironmentFixture.setUp()

    executionConsoleFixture = GradleExecutionViewFixture(project, executionEnvironmentFixture)
    executionConsoleFixture.setUp()

    buildViewFixture = BuildViewTestFixture(project)
    buildViewFixture.setUp()
  }

  override fun tearDown() {
    runAll(
      { buildViewFixture.tearDown() },
      { executionConsoleFixture.tearDown() },
      { executionEnvironmentFixture.tearDown() },
      { Disposer.dispose(testDisposable) },
      { cleanupProjectBuildDirectory() },
      { super.tearDown() },
    )
  }

  // '--rerun-tasks' corrupts gradle build caches fo gradle versions before 4.0 (included)
  private fun cleanupProjectBuildDirectory() {
    runWriteActionAndWait {
      projectRoot.deleteRecursively("build")
    }
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

  fun executeTasks(commandLine: String, isRunAsTest: Boolean = true) {
    val runManager = RunManager.getInstance(project)
    val runConfigurationName = "GradleTestExecutionTestCase (" + LocalTimeCounter.currentTime() + ")"
    val runnerSettings = runManager.createConfiguration(runConfigurationName, GradleExternalTaskConfigurationType::class.java)
    val runConfiguration = runnerSettings.configuration as GradleRunConfiguration
    runConfiguration.rawCommandLine = commandLine
    runConfiguration.isRunAsTest = isRunAsTest
    runConfiguration.settings.externalProjectPath = projectPath
    runConfiguration.settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    val executorId = DefaultRunExecutor.EXECUTOR_ID
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)!!
    val runner = ProgramRunner.getRunner(executorId, runConfiguration)!!
    Assertions.assertEquals(ExternalSystemConstants.RUNNER_ID, runner.runnerId)
    val environment = ExecutionEnvironment(executor, runner, runnerSettings, project)
    waitForAnyGradleTaskExecution {
      runWriteActionAndWait {
        runner.execute(environment)
      }
    }
  }

  open fun <R> waitForAnyGradleTaskExecution(action: () -> R) {
    executionEnvironmentFixture.assertExecutionEnvironmentIsReady {
      waitForGradleEventDispatcherClosing {
        waitForAnyExecution(project) {
          org.jetbrains.plugins.gradle.testFramework.util.waitForAnyGradleTaskExecution {
            action()
          }
        }
      }
    }
  }

  fun assertBuildExecutionTree(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertBuildViewTreeEquals { treeString ->
      val actualTree = buildTree(treeString!!)
      TreeAssertion.assertTree(actualTree) {
        assertNode("", assert)
      }
    }
  }

  fun assertTestConsoleContains(expected: String) {
    executionConsoleFixture.assertTestConsoleContains(expected)
  }

  fun assertTestConsoleDoesNotContain(expected: String) {
    executionConsoleFixture.assertTestConsoleDoesNotContain(expected)
  }

  fun TreeAssertion.Node<AbstractTestProxy>.assertTestConsoleContains(expectedTextSample: String) {
    executionConsoleFixture.assertTestConsoleContains(this, expectedTextSample)
  }

  fun TreeAssertion.Node<AbstractTestProxy>.assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    executionConsoleFixture.assertTestConsoleDoesNotContain(this, unexpectedTextSample)
  }

  fun assertRunTreeView(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    executionConsoleFixture.assertRunTreeView(assert)
  }

  fun assertTestTreeView(assert: TreeAssertion<AbstractTestProxy>.() -> Unit) {
    executionConsoleFixture.assertTestTreeView(assert)
  }

  fun assertRunTreeViewIsEmpty() {
    executionConsoleFixture.assertRunTreeViewIsEmpty()
  }

  fun assertTestTreeViewIsEmpty() {
    executionConsoleFixture.assertTestTreeViewIsEmpty()
  }

  fun TreeAssertion.Node<AbstractTestProxy>.assertPsiLocation(className: String, methodName: String? = null) {
    executionConsoleFixture.assertPsiLocation(this, className, methodName)
  }
}