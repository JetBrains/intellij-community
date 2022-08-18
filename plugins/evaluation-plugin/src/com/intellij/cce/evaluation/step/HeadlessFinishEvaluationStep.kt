package com.intellij.cce.evaluation.step

import com.intellij.cce.workspace.EvaluationWorkspace
import kotlin.system.exitProcess

class HeadlessFinishEvaluationStep : FinishEvaluationStep() {
  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    print("Evaluation completed. ")
    if (workspace.getReports().isEmpty()) {
      println(" Workspace: ${workspace.path()}")
    } else {
      println("Reports:")
      workspace.getReports().forEach { println("${it.key}: ${it.value}") }
    }
    exitProcess(0)
  }
}
