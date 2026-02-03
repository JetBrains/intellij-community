// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime

import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.executeState
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration
import org.jetbrains.concurrency.resolvedPromise

internal class DeployToServerRunner : ProgramRunner<RunnerSettings> {
  override fun getRunnerId() = "DeployToServer"

  override fun execute(environment: ExecutionEnvironment) {
    val state = environment.state ?: return
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
      resolvedPromise(executeState(state, environment, this))
    }
  }

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (profile !is DeployToServerRunConfiguration<*, *>) {
      return false
    }
    if (executorId == DefaultRunExecutor.EXECUTOR_ID) {
      return true
    }
    if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
      return profile.serverType.createDebugConnector() != null
    }
    return false
  }
}