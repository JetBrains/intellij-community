package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.cce.workspace.Config
import com.intellij.openapi.project.Project

class ActionsInterpretationOnNewWorkspaceStep(config: Config, actionsInvoker: ActionsInvoker, project: Project) :
  CreateWorkspaceStep(
    config,
    ActionsInterpretationHandler(config.interpret, config.language, actionsInvoker, project),
    project) {

  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"
}