package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl

private const val BUILD_PROJECT = "build.project"

internal class BuildProjectStep: SmartUpdateStep {
  override val id = BUILD_PROJECT
  override val stepName = SmartUpdateBundle.message("checkbox.build.project")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val start = System.currentTimeMillis()
    ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)
    ProjectTaskManager.getInstance(project).buildAllModules().onSuccess {
      SmartUpdateUsagesCollector.logBuild(System.currentTimeMillis() - start, true)
      onSuccess.invoke()
    }.onError {
      SmartUpdateUsagesCollector.logBuild(System.currentTimeMillis() - start, false)
    }
  }
}