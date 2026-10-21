package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

private const val EXECUTION_MODULE = "com.intellij/intellij.platform.execution"
private const val EXECUTION_IMPL_MODULE = "com.intellij/intellij.platform.execution.impl"
private const val DEBUGGER_MODULE = "com.intellij/intellij.platform.debugger"

@Remote("com.intellij.execution.RunManager", plugin = EXECUTION_MODULE, rdTarget = RdTarget.BACKEND)
interface RunManager {
  fun getInstance(project: Project): RunManager
  val allSettings: List<RunnerAndConfigurationSettings>
}

@Remote("com.intellij.execution.RunnerAndConfigurationSettings", plugin = EXECUTION_MODULE, rdTarget = RdTarget.BACKEND)
interface RunnerAndConfigurationSettings {
  fun getName(): String
}

@Remote("com.intellij.execution.executors.DefaultRunExecutor", plugin = EXECUTION_MODULE, rdTarget = RdTarget.BACKEND)
interface DefaultRunExecutor : Executor {
  fun getRunExecutorInstance(): DefaultRunExecutor
}

@Remote("com.intellij.execution.Executor", plugin = EXECUTION_MODULE, rdTarget = RdTarget.BACKEND)
interface Executor

@Remote("com.intellij.execution.executors.DefaultDebugExecutor", plugin = DEBUGGER_MODULE, rdTarget = RdTarget.BACKEND)
interface DefaultDebugExecutor : Executor {
  fun getDebugExecutorInstance(): DefaultDebugExecutor
}

@Remote("com.intellij.execution.ProgramRunnerUtil", plugin = EXECUTION_IMPL_MODULE, rdTarget = RdTarget.BACKEND)
interface ProgramRunnerUtil {
  fun executeConfiguration(settings: RunnerAndConfigurationSettings, executor: Executor)
}

fun Driver.executeConfiguration(
  configurationName: String,
  project: Project = singleProject(rdTarget = RdTarget.BACKEND),
  isDebug: Boolean = false,
) {
  val runManager = utility(RunManager::class).getInstance(project = project)

  val requiredConfiguration = with(runManager.allSettings) {
    this.singleOrNull { it.getName().contains(configurationName) }
    ?: error("Run configuration '$configurationName' not found, available configurations:${this.map { it.getName() }}")
  }

  with(if (isDebug) utility(DefaultDebugExecutor::class).getDebugExecutorInstance() else utility(DefaultRunExecutor::class).getRunExecutorInstance()) {
    utility(ProgramRunnerUtil::class).executeConfiguration(requiredConfiguration, this)
  }

}