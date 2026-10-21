// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToCommit
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogNotificationIdsHolder
import com.intellij.vcs.log.ui.details.commit.CommitMessagePopup
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.ui.frame.VcsLogCommitSelectionListenerForDetails

private val LOG = logger<MaximizeCommitMessageAction>()

/**
 * Shows commit messages in a popup.
 * The message comes from the commit details panel that owns the toolbar button.
 * Without the panel, the messages come from the commits selected in the log table.
 * The popup shows at most [VcsLogCommitSelectionListenerForDetails.MAX_COMMITS_TO_LOAD] of them.
 */
internal class MaximizeCommitMessageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val hasPanelCommit = e.getData(VcsLogInternalDataKeys.COMMIT_DETAILS_PANEL)?.commitPresentation != null
    val withinLogUi = e.getData(VcsLogInternalDataKeys.LOG_UI_EX) != null
    val hasSelection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.rows?.isNotEmpty() == true
    e.presentation.isEnabledAndVisible = e.project != null && withinLogUi && (hasPanelCommit || hasSelection)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val panelPopup = e.getData(VcsLogInternalDataKeys.COMMIT_DETAILS_PANEL)?.createMessagePopup(project)
    if (panelPopup != null) {
      panelPopup.showCenteredInCurrentWindow(project)
      return
    }

    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
    val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA) ?: return
    val logUi = e.getData(VcsLogInternalDataKeys.LOG_UI_EX) ?: return
    val selectedCommitCount = selection.rows.size
    if (selectedCommitCount == 0) return

    val ids = selection.ids.take(VcsLogCommitSelectionListenerForDetails.MAX_COMMITS_TO_LOAD)
    logData.miniDetailsGetter.loadCommitsData(ids, { commits ->
      if (project.isDisposed || commits.isEmpty()) return@loadCommitsData
      val presentations = commits.map { CommitPresentationUtil.buildPresentation(project, it, mutableSetOf()) }
      val navigate = { commitId: CommitId ->
        logUi.jumpToCommit(commitId.hash, commitId.root, false, true)
        Unit
      }
      CommitMessagePopup.createPopup(project, presentations, navigate, selectedCommitCount).showCenteredInCurrentWindow(project)
    }, { handleDetailsLoadingError(project, it) }, null)
  }
}

private fun handleDetailsLoadingError(project: Project, error: Throwable) {
  LOG.warn("Could not load commit details", error)
  val message = VcsLogBundle.message("vcs.log.error.could.not.load.commit.details", error.message)
  VcsNotifier.getInstance(project).notifyError(VcsLogNotificationIdsHolder.LOAD_COMMIT_DETAILS_ERROR, "", message)
}
