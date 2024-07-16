// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.ProjectOpeningUtils
import com.intellij.cce.evaluation.FinishEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ex.ApplicationEx.FORCE_EXIT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import kotlin.system.exitProcess

class HeadlessFinishEvaluationStep(private val project: Project) : FinishEvaluationStep {
  override fun start(workspace: EvaluationWorkspace, withErrors: Boolean) {
    if (withErrors) {
      println("Evaluation completed with errors.")
      ProjectOpeningUtils.closeProject(project)
      exit(exitCode = 1)
    } else {
      print("Evaluation completed. ")
      if (workspace.getReports().isEmpty()) {
        println(" Workspace: ${workspace.path()}")
      }
      else {
        println("Reports:")
        workspace.getReports().forEach { println("${it.key}: ${it.value}") }
      }
      ProjectOpeningUtils.closeProject(project)
      exit(exitCode = 0)
    }
  }

  private fun exit(exitCode: Int) = try {
    ApplicationManagerEx.getApplicationEx().exit(FORCE_EXIT, exitCode)
  } catch (t: Throwable) {
    exitProcess(exitCode)
  }
}
