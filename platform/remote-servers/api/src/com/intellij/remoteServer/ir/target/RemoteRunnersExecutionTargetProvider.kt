package com.intellij.remoteServer.ir.target

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.remoteServer.ir.configuration.RemoteTargetsManager
import com.intellij.remoteServer.ir.configuration.getTargetType

class RemoteRunnersExecutionTargetProvider : ExecutionTargetProvider() {

  override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
    val allConfigs = RemoteTargetsManager.instance.resolvedConfigs()
    return allConfigs
      .map { it.getTargetType().createExecutionTarget(project, it) }
      .filterNotNull()
  }
}