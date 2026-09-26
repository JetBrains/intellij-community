// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.annotations.ApiStatus.Obsolete

interface GradleExecutionTestFixture : IdeaTestFixture {

  fun createRunnerSettings(
    commandLine: String,
    isRunAsTest: Boolean
  ): RunnerAndConfigurationSettings

  fun createExecutionEnvironment(
    runnerSettings: RunnerAndConfigurationSettings,
    isDebug: Boolean
  ): ExecutionEnvironment

  /**
   * obsolete: Use [executeAsync] instead.
   */
  @Obsolete
  fun execute(environment: ExecutionEnvironment)

  suspend fun executeAsync(environment: ExecutionEnvironment)

  /**
   * obsolete: Use [executeTasks] instead.
   */
  @Obsolete
  fun executeTasks(commandLine: String, isRunAsTest: Boolean, isDebug: Boolean)

  suspend fun executeTasksAsync(commandLine: String, isRunAsTest: Boolean, isDebug: Boolean)

  /**
   * obsolete: Use [assertAnyGradleTaskExecutionAsync] instead.
   */
  @Obsolete
  fun <R> assertAnyGradleTaskExecution(numExec: Int, action: () -> R): R

  suspend fun <R> assertAnyGradleTaskExecutionAsync(numExec: Int, action: suspend () -> R): R

  /**
   * obsolete: Use [awaitAnyGradleTaskExecution] instead.
   */
  @Obsolete
  fun <R> waitForAnyGradleTaskExecution(action: () -> R): R

  suspend fun <R> awaitAnyGradleTaskExecution(action: suspend () -> R): R
}