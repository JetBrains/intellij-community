// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

/**
 * Shows the settings context of the selected file, with the content roots it was built from. It is an internal
 * action. Use it to check by hand where a file resolves its settings.
 *
 * The roots come from [fileContextRootUrls], so the report shows the roots of the side that runs the action.
 * A split session shows the action of each side in the client, as [ActionRemoteBehaviorSpecification.Duplicated] says.
 */
internal class SettingsContextDumpAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val title = templatePresentation.text ?: DEFAULT_TITLE
    // A thin-client file answers getPath() from the backend, and it waits for the answer, so this stays off the EDT.
    ApplicationManager.getApplication().executeOnPooledThread {
      val report = runReadActionBlocking { report(project, file) }
      ApplicationManager.getApplication().invokeLater { SettingsContextReportDialog(project, title, report).show() }
    }
  }

  private fun report(project: Project, file: VirtualFile?): @NlsSafe String {
    val roots = fileContextRootUrls(project)
    val context = if (file == null) SettingsContext.project(project) else SettingsContext.file(file, project)
    val chain = generateSequence(context) { it.parentContext }
    return buildString {
      appendLine("project: ${project.locationHash}")
      appendLine("file: ${file?.path ?: "none selected"}")
      appendLine("file system: ${file?.fileSystem?.protocol ?: "none"}")
      appendLine()
      appendLine("content roots (${roots.size}):")
      roots.forEach { appendLine("  $it") }
      appendLine()
      appendLine("context chain, narrowest first:")
      chain.forEach { appendLine("  ${it.kind}  ${it.identity}") }
    }
  }

  private companion object {
    @NlsSafe
    const val DEFAULT_TITLE: String = "Settings Context"
  }
}

// Shown from the backend as well, where Lux projects it to the client.
@Suppress("SplitModeApiUsage")
private class SettingsContextReportDialog(
  project: Project,
  reportTitle: @DialogTitle String,
  private val report: @NlsSafe String,
) : DialogWrapper(project) {
  init {
    title = reportTitle
    init()
  }

  override fun createCenterPanel(): JComponent =
    JBScrollPane(JBTextArea(report).apply { isEditable = false }).apply { preferredSize = Dimension(800, 400) }

  override fun createActions(): Array<Action> = arrayOf(okAction)
}
