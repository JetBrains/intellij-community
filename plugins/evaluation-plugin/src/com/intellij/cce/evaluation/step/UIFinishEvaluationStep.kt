package com.intellij.cce.evaluation.step

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.dialog.OpenBrowserDialog
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class UIFinishEvaluationStep(private val project: Project) : FinishEvaluationStep() {
  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    if (workspace.getReports().isNotEmpty()) ApplicationManager.getApplication().invokeAndWait {
      val dialog = OpenBrowserDialog(workspace.getReports().map { it.key })
      if (dialog.showAndGet()) {
        dialog.reportNamesForOpening.forEach {
          BrowserUtil.browse(workspace.getReports()[it].toString())
        }
      }
    }
    else ApplicationManager.getApplication().invokeAndWait {
      Messages.showInfoMessage(project,
                               EvaluationPluginBundle.message("evaluation.completed.text"),
                               EvaluationPluginBundle.message("evaluation.completed.title"))
    }
    return workspace
  }
}