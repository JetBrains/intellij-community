// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.FinishEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager

class HeadlessFinishEvaluationStep : FinishEvaluationStep {
  override fun start(workspace: EvaluationWorkspace, withErrors: Boolean) {
    if (withErrors) {
      println("Evaluation completed with errors.")
      ApplicationManager.getApplication().exit(true, false, false, 1)
    } else {
      print("Evaluation completed. ")
      if (workspace.getReports().isEmpty()) {
        println(" Workspace: ${workspace.path()}")
      }
      else {
        println("Reports:")
        workspace.getReports().forEach { println("${it.key}: ${it.value}") }
      }
      ApplicationManager.getApplication().exit(true, false, false)
    }
  }
}
