package com.intellij.cce.evaluation

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class UIEvaluationAbortHandler(private val project: Project) : EvaluationAbortHandler {
  override fun onError(error: Throwable, stage: String) {
    Messages.showErrorDialog(project, error.localizedMessage, EvaluationPluginBundle.message("evaluation.error.title", stage))
  }

  override fun onCancel(stage: String) {
    Messages.showInfoMessage(project,
                             EvaluationPluginBundle.message("evaluation.cancel.text", stage),
                             EvaluationPluginBundle.message("evaluation.cancel.title"))
  }
}