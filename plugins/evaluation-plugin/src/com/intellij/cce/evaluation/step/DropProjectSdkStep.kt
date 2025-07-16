package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ForegroundEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager

class DropProjectSdkStep(private val project: Project) : ForegroundEvaluationStep {
  override val name: String = "Drop project SDK"
  override val description: String = "Drop existing project SDK"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.run<Throwable> {
        val currentSdk = ProjectRootManager.getInstance(project).projectSdk
        if (currentSdk != null) {
          println("Current JDK: ${currentSdk.name} (${currentSdk.homePath})")
          ProjectRootManager.getInstance(project).projectSdk = null
        }
      }
    }
    println("Project SDK has been dropped.")
    return workspace
  }
}