package com.intellij.remoteServer.ir.target

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class RemoteRunnersExecutionTargetProvider : ExecutionTargetProvider() {

  override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
    return RemoteTargetsManager.instance.targets.resolvedConfigs()
      .map { it.getTargetType().createExecutionTarget(project, it) }
      .filterNotNull()
  }
}