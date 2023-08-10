// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.EvaluationStep

abstract class FinishEvaluationStep : EvaluationStep {
  override val name: String = "Evaluation completed"
  override val description: String = "Correct termination of evaluation"
}