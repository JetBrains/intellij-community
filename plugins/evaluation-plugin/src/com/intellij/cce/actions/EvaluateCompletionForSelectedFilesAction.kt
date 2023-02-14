package com.intellij.cce.actions

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.dialog.FullSettingsDialog
import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class EvaluateCompletionForSelectedFilesAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: emptyList<VirtualFile>()

    val language2files = FilesHelper.getFiles(project, files)
    if (language2files.isEmpty()) {
      Messages.showInfoMessage(project,
                               EvaluationPluginBundle.message("EvaluateCompletionForSelectedFilesAction.error.text"),
                               EvaluationPluginBundle.message("EvaluateCompletionForSelectedFilesAction.error.title"))
      return
    }

    val dialog = FullSettingsDialog(project, files, language2files)
    val result = dialog.showAndGet()
    if (!result) return

    val config = dialog.buildConfig()
    val workspace = EvaluationWorkspace.create(config)
    val process = EvaluationProcess.build({
                                            shouldGenerateActions = true
                                            shouldInterpretActions = true
                                            shouldGenerateReports = true
                                          }, BackgroundStepFactory(config, project, false, null, EvaluationRootInfo(true)))
    process.startAsync(workspace)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.isNotEmpty() ?: false
    e.presentation.isVisible = e.place == ActionPlaces.ACTION_SEARCH || !ApplicationInfo.getInstance().build.isSnapshot
  }
}