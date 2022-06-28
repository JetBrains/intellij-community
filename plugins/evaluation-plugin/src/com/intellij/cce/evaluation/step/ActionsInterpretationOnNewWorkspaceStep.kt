package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.interpreter.CompletionInvoker
import com.intellij.cce.workspace.Config
import com.intellij.openapi.project.Project

class ActionsInterpretationOnNewWorkspaceStep(config: Config, completionInvoker: CompletionInvoker, project: Project, isHeadless: Boolean) :
  CreateWorkspaceStep(
    config,
    ActionsInterpretationHandler(config.interpret, config.language, completionInvoker, project),
    project,
    isHeadless) {

  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"
}