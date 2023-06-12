package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager

const val BUILD_PROJECT = "build.project"

class BuildProjectStep: SmartUpdateStep {
  override val id = BUILD_PROJECT

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess { onSuccess.invoke() }
  }
}