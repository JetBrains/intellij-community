package com.intellij.cce.evaluation

import com.intellij.cce.workspace.EvaluationWorkspace

interface EvaluationStep {
  val name: String
  val description: String

  fun start(workspace: EvaluationWorkspace): EvaluationWorkspace?
}