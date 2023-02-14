package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager

class BuildStep: SmartUpdateStep {
  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess { onSuccess.invoke() }
  }

  override fun isRequested(options: SmartUpdate.Options) = options.buildProject
}