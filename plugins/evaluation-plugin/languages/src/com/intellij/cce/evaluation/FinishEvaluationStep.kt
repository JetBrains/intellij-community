// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.workspace.EvaluationWorkspace

interface FinishEvaluationStep {
  fun start(workspace: EvaluationWorkspace, withErrors: Boolean)

  class EvaluationCompletedWithErrorsException : Exception("Evaluation completed with errors.")
}
