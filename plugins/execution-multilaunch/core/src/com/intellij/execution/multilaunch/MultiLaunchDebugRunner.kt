package com.intellij.execution.multilaunch

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.wm.ToolWindowId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchDebugRunner : ProgramRunner<RunnerSettings> {
  override fun getRunnerId() = "MultiLaunchDebugRunner"

  override fun canRun(executorId: String, profile: RunProfile) =
    executorId == ToolWindowId.DEBUG && profile is MultiLaunchConfiguration

  override fun execute(environment: ExecutionEnvironment) {
    environment.state?.execute(ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG), this)
  }
}