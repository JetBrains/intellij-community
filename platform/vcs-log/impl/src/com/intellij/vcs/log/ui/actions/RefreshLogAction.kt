// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.TabbedContent
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import kotlinx.coroutines.launch

private val LOG = logger<RefreshLogAction>()

internal class RefreshLogAction : RefreshAction() {
  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this)

    val logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER) ?: return
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI) ?: return

    val logData = logManager.dataManager
    if (logData.isRefreshInProgress.value) return

    // diagnostic for possible refresh problems
    if (ui is VcsLogUiEx) {
      val refresher = ui.refresher
      if (!refresher.isValid) {
        val message = "Trying to refresh invalid log tab '${ui.id}'."
        if (!logManager.dataManager.progress.isRunning) {
          LOG.error(message, *collectDiagnosticInformation(e.project, logManager))
        }
        else {
          LOG.warn(message)
        }
        refresher.setValid(true, false)
      }
    }

    val project = logData.project
    val roots = VcsLogUtil.getVisibleRoots(ui)
    e.coroutineScope.launch {
      VcsLogRefreshActionListener.EP_NAME.forEachExtensionSafe { listener -> listener.beforeRefresh(project, roots) }
      logData.refresh(roots)
    }
  }

  override fun update(e: AnActionEvent) {
    val logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER)
    e.presentation.isEnabledAndVisible = logManager != null && e.getData(VcsLogDataKeys.VCS_LOG_UI) != null
  }
}

private fun collectDiagnosticInformation(project: Project?, logManager: VcsLogManager): Array<Attachment> {
  val result = mutableListOf<Attachment>()
  result.add(Attachment("log-windows.txt", "Log Windows:\n${logManager.getLogUiInformation()}"))

  if (project != null) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    if (toolWindow != null) {
      val contentDump = toolWindow.contentManager.contents.joinToString("\n") { content ->
        if (content is TabbedContent) {
          "$content, tabs=[${content.tabs.joinToString(", ") { it.first }}]"
        }
        else {
          content.toString()
        }
      }
      result.add(Attachment("vcs-tool-window-content.txt",
                            "Tool Window ${toolWindow.title} (${toolWindow.type}):\n$contentDump"))
    }
  }

  return result.toTypedArray()
}
