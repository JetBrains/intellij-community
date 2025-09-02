// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll
import com.intellij.util.LocalTimeCounter
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.testFramework.util.*
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleTaskExecutionOperation
import org.junit.jupiter.api.Assertions

class GradleExecutionTestFixtureImpl(
  private val project: Project,
  private val projectRoot: VirtualFile
) : GradleExecutionTestFixture {

  private lateinit var taskExecutionLeakTracker: OperationLeakTracker
  private lateinit var executionLeakTracker: OperationLeakTracker

  override fun setUp() {
    taskExecutionLeakTracker = OperationLeakTracker { getGradleTaskExecutionOperation(project, it) }
    taskExecutionLeakTracker.setUp()

    executionLeakTracker = OperationLeakTracker { getExecutionOperation(project, it) }
    executionLeakTracker.setUp()
  }

  override fun tearDown() {
    RunAll.Companion.runAll(
      { executionLeakTracker.tearDown() },
      { taskExecutionLeakTracker.tearDown() }
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

  override fun <R> assertAnyGradleTaskExecution(numExec: Int, action: () -> R): R {
    return taskExecutionLeakTracker.withAllowedOperation(numExec) {
      executionLeakTracker.withAllowedOperation(numExec) {
        action()
      }
    }
  }

  override suspend fun <R> assertAnyGradleTaskExecutionAsync(numExec: Int, action: suspend () -> R): R {
    return taskExecutionLeakTracker.withAllowedOperationAsync(numExec) {
      executionLeakTracker.withAllowedOperationAsync(numExec) {
        action()
      }
    }
  }

  override fun <R> waitForAnyGradleTaskExecution(action: () -> R): R {
    return assertAnyGradleTaskExecution(numExec = 1) {
      waitForGradleEventDispatcherClosing {
        waitForAnyExecution(project) {
          org.jetbrains.plugins.gradle.testFramework.util.waitForAnyGradleTaskExecution {
            action()
          }
        }
      }
    }
  }

  override suspend fun <R> awaitAnyGradleTaskExecution(action: suspend () -> R): R {
    return assertAnyGradleTaskExecutionAsync(numExec = 1) {
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