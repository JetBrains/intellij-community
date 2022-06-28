package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.TwoWorkspaceHandler
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project

abstract class CreateWorkspaceStep(
  private val config: Config,
  private val handler: TwoWorkspaceHandler,
  project: Project,
  isHeadless: Boolean) : BackgroundEvaluationStep(project, isHeadless) {

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val newWorkspace = EvaluationWorkspace.create(config)
    handler.invoke(workspace, newWorkspace, progress)
    return newWorkspace
  }
}