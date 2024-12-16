package com.intellij.cce.evaluation.step

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.openapi.extensions.ExtensionPointName

interface ExtraEvaluationStepProvider {
  fun extraStepsFor(language: Language, strategy: EvaluationStrategy): List<EvaluationStep>

  companion object {
    public val EP_NAME: ExtensionPointName<ExtraEvaluationStepProvider> =
      ExtensionPointName.Companion.create<ExtraEvaluationStepProvider>("com.intellij.cce.extraEvaluationStepProvider")
  }
}