package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

private const val EXECUTION_MODULE = "com.intellij/intellij.platform.execution"
private const val EXECUTION_IMPL_MODULE = "com.intellij/intellij.platform.execution.impl"

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
interface DefaultRunExecutor {
  fun getRunExecutorInstance(): DefaultRunExecutor
}

@Remote("com.intellij.execution.ProgramRunnerUtil", plugin = EXECUTION_IMPL_MODULE, rdTarget = RdTarget.BACKEND)
interface ProgramRunnerUtil {
  fun executeConfiguration(settings: RunnerAndConfigurationSettings, runExecutor: DefaultRunExecutor)
}

fun Driver.executeRunConfiguration(configurationName: String, project: Project = singleProject(rdTarget = RdTarget.BACKEND)) {
  val runManager = utility(RunManager::class).getInstance(project = project)

  val configSettings = runManager.allSettings
                         .singleOrNull { it.getName().contains(configurationName) }
                       ?: error("Run configuration '$configurationName' not found")

  utility(ProgramRunnerUtil::class)
    .executeConfiguration(configSettings, utility(DefaultRunExecutor::class).getRunExecutorInstance())
}