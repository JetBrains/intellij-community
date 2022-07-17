package com.intellij.cce.actions

import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.vfs.VirtualFile

class GenerateEvaluationReportAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dirs = getFiles(e)
    val config = dirs.map { EvaluationWorkspace.open(it.path) }.buildMultipleEvaluationsConfig()
    val outputWorkspace = EvaluationWorkspace.create(config)
    val process = EvaluationProcess.build({
                                            shouldGenerateReports = true
                                          }, BackgroundStepFactory(config, project, false, dirs.map { it.path }, EvaluationRootInfo(true)))
    process.startAsync(outputWorkspace)
  }

  override fun update(e: AnActionEvent) {
    if (e.place != ActionPlaces.ACTION_SEARCH && ApplicationInfo.getInstance().build.isSnapshot) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val files = getFiles(e)
    e.presentation.isEnabled = files.isNotEmpty() && files.all { it.isDirectory && it.children.any { it.name == ConfigFactory.DEFAULT_CONFIG_NAME } }
  }

  private fun getFiles(e: AnActionEvent): List<VirtualFile> = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: emptyList()
}