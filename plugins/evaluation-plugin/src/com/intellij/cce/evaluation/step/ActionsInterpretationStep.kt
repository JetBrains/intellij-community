package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.interpreter.CompletionInvoker
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project

class ActionsInterpretationStep(
  private val config: Config.ActionsInterpretation,
  private val language: String,
  private val completionInvoker: CompletionInvoker,
  project: Project,
  isHeadless: Boolean) : BackgroundEvaluationStep(project, isHeadless) {
  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    ActionsInterpretationHandler(config, language, completionInvoker, project).invoke(workspace, workspace, progress)
    return workspace
  }
}