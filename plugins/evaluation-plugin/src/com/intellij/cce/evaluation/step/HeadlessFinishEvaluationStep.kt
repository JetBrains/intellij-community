// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.FinishEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace

class HeadlessFinishEvaluationStep : FinishEvaluationStep {
  override fun start(workspace: EvaluationWorkspace, withErrors: Boolean) {
    if (withErrors) {
      throw FinishEvaluationStep.EvaluationCompletedWithErrorsException()
    } else {
      print("Evaluation completed. ")
      if (workspace.getReports().isEmpty()) {
        println(" Workspace: ${workspace.path()}")
      }
      else {
        println("Reports:")
        workspace.getReports().forEach { println("${it.key}: file://${it.value.toString().escape()}") }
      }
    }
  }

  private fun String.escape(): String {
    return replace(" ", "%20")
  }
}
