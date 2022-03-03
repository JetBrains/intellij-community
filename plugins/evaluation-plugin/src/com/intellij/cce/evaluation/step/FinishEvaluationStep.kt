package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.EvaluationStep

abstract class FinishEvaluationStep : EvaluationStep {
  override val name: String = "Evaluation completed"
  override val description: String = "Correct termination of evaluation"
}