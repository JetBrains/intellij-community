package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager

const val BUILD_PROJECT = "build.project"

class BuildProjectStep: SmartUpdateStep {
  override val id = BUILD_PROJECT
  override val stepName = SmartUpdateBundle.message("checkbox.build.project")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val start = System.currentTimeMillis()
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess {
      SmartUpdateUsagesCollector.logBuild(System.currentTimeMillis() - start, true)
      onSuccess.invoke()
    }.onError {
      SmartUpdateUsagesCollector.logBuild(System.currentTimeMillis() - start, false)
    }
  }
}