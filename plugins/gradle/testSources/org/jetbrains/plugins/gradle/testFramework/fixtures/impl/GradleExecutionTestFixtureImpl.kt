// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.LocalTimeCounter
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionOutputFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionViewFixture
import org.jetbrains.plugins.gradle.testFramework.util.awaitAnyExecution
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleEventDispatcherClosing
import org.jetbrains.plugins.gradle.testFramework.util.waitForAnyExecution
import org.jetbrains.plugins.gradle.testFramework.util.waitForGradleEventDispatcherClosing
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions

class GradleExecutionTestFixtureImpl(
  private val project: Project,
  private val projectRoot: VirtualFile
) : GradleExecutionTestFixture {

  private lateinit var executionOutputFixture: GradleExecutionOutputFixture
  private lateinit var executionEnvironmentFixture: GradleExecutionEnvironmentFixture
  private lateinit var executionConsoleFixture: GradleExecutionViewFixture
  private lateinit var buildViewFixture: BuildViewTestFixture

  override fun getExecutionEnvironment(): ExecutionEnvironment {
    return executionEnvironmentFixture.getExecutionEnvironment()
  }

  override fun setUp() {
    executionOutputFixture = GradleExecutionOutputFixtureImpl(project)
    executionOutputFixture.setUp()

    executionEnvironmentFixture = GradleExecutionEnvironmentFixtureImpl(project)
    executionEnvironmentFixture.setUp()

    executionConsoleFixture = GradleExecutionViewFixtureImpl(project, executionEnvironmentFixture)
    executionConsoleFixture.setUp()

    buildViewFixture = BuildViewTestFixture(project)
    buildViewFixture.setUp()
  }

  override fun tearDown() {
    RunAll.Companion.runAll(
      { buildViewFixture.tearDown() },
      { executionConsoleFixture.tearDown() },
      { executionEnvironmentFixture.tearDown() },
      { executionOutputFixture.tearDown() }
    )
  }

  override fun createRunnerSettings(
    commandLine: String,
    isRunAsTest: Boolean
  ): RunnerAndConfigurationSettings {
    val runManager = RunManager.Companion.getInstance(project)
    val runConfigurationName = "GradleExecutionTestFixture (" + LocalTimeCounter.currentTime() + ")"
    val runnerSettings = runManager.createConfiguration(runConfigurationName, GradleExternalTaskConfigurationType::class.java)
    val runConfiguration = runnerSettings.configuration as GradleRunConfiguration
    runConfiguration.settings.externalProjectPath = projectRoot.path
    runConfiguration.settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    runConfiguration.rawCommandLine = commandLine
    runConfiguration.isRunAsTest = isRunAsTest
    return runnerSettings
  }

  override fun createExecutionEnvironment(
    runnerSettings: RunnerAndConfigurationSettings,
    isDebug: Boolean
  ): ExecutionEnvironment {
    val executorId = if (isDebug) DefaultDebugExecutor.EXECUTOR_ID else DefaultRunExecutor.EXECUTOR_ID
    val runnerId = if (isDebug) ExternalSystemConstants.DEBUG_RUNNER_ID else ExternalSystemConstants.RUNNER_ID
    val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)!!
    val runner = ProgramRunner.getRunner(executorId, runnerSettings.configuration)!!
    val environment = ExecutionEnvironment(executor, runner, runnerSettings, project)

    Assertions.assertEquals(runnerId, runner.runnerId)

    return environment
  }

  override fun execute(environment: ExecutionEnvironment) {
    waitForAnyGradleTaskExecution {
      runWriteActionAndWait {
        environment.runner.execute(environment)
      }
    }
  }

  override suspend fun executeAsync(environment: ExecutionEnvironment) {
    awaitAnyGradleTaskExecution {
      edtWriteAction {
        environment.runner.execute(environment)
      }
    }
  }

  override fun executeTasks(commandLine: String, isRunAsTest: Boolean, isDebug: Boolean) {
    val runnerSettings = createRunnerSettings(commandLine, isRunAsTest)
    val environment = createExecutionEnvironment(runnerSettings, isDebug)
    execute(environment)
  }

  override suspend fun executeTasksAsync(commandLine: String, isRunAsTest: Boolean, isDebug: Boolean) {
    val runnerSettings = createRunnerSettings(commandLine, isRunAsTest)
    val environment = createExecutionEnvironment(runnerSettings, isDebug)
    executeAsync(environment)
  }

  override fun <R> waitForAnyGradleTaskExecution(action: () -> R): R {
    return executionOutputFixture.assertExecutionOutputIsReady {
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
  }

  override suspend fun <R> awaitAnyGradleTaskExecution(action: suspend () -> R): R {
    return executionOutputFixture.assertExecutionOutputIsReadyAsync {
      executionEnvironmentFixture.assertExecutionEnvironmentIsReadyAsync {
        awaitGradleEventDispatcherClosing {
          awaitAnyExecution(project) {
            org.jetbrains.plugins.gradle.testFramework.util.awaitAnyGradleTaskExecution {
              action()
            }
          }
        }
      }
    }
  }

  override fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertSyncViewTree(assert)
  }

  override fun assertBuildViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertBuildViewTree(assert)
  }

  override fun assertRunViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    executionConsoleFixture.assertRunTreeView(assert)
  }

  override fun assertRunViewTreeIsEmpty() {
    executionConsoleFixture.assertRunTreeViewIsEmpty()
  }

  override fun assertPsiLocation(
    testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>,
    className: String, methodName: String?, parameterName: String?
  ) {
    executionConsoleFixture.assertPsiLocation(testAssertion, className, methodName, parameterName)
  }

  override fun assertTestEventsContain(className: String, methodName: String?) {
    executionOutputFixture.assertTestEventContain(className, methodName)
  }

  override fun assertTestEventsDoNotContain(className: String, methodName: String?) {
    executionOutputFixture.assertTestEventDoesNotContain(className, methodName)
  }

  override fun assertTestEventsWereNotReceived() {
    executionOutputFixture.assertTestEventsWasNotReceived()
  }
}