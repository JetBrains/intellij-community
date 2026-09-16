// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel.ChangelistData
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogCommitDataCache
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.history.FileHistoryModel
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.CommitDetailsLoader
import com.intellij.vcs.log.ui.frame.VcsLogCommitSelectionListenerForDetails
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowAllAffectedFromHistoryAction : FileHistoryOneCommitAction<VcsFullCommitDetails>() {
  override fun getCache(logData: VcsLogData): VcsLogCommitDataCache<VcsFullCommitDetails> = logData.commitDetailsGetter

  override fun loadData(logData: VcsLogData,
                        selection: VcsLogCommitSelection,
                        onSuccess: (List<VcsFullCommitDetails>) -> Unit,
                        onError: (Throwable) -> Unit) {
    logData.commitDetailsGetter.loadCommitsData(selection.ids, { details -> onSuccess(details) }, { t -> onError(t) }, null)
  }

  override fun performAction(project: Project,
                             model: FileHistoryModel,
                             detail: VcsFullCommitDetails,
                             e: AnActionEvent) {
    val logData = e.getData(VcsLogInternalDataKeys.LOG_DATA) ?: return
    val file = model.getPathInCommit(detail.id)
    val title = VcsLogBundle.message("dialog.title.paths.affected.by.commit", detail.id.toShortString())

    val panel = LoadingCommittedChangeListPanel(project) { parent ->
      createCommitDetailsPanel(project, logData, detail, parent)
    }
    panel.setCommitDetailsProportion(DETAILS_PROPORTION)
    panel.loadChangesInBackground {
      val committedChangeList = VcsLogUtil.createCommittedChangeList(detail)
      ChangelistData(committedChangeList, file)
    }

    ChangeListViewerDialog.show(project, title, panel)
  }

  /**
   * Builds the same commit details panel that the log tool window and the file history panel use.
   * The action already loaded the commit, so the panel gets the details at once and loads nothing.
   */
  private fun createCommitDetailsPanel(project: Project,
                                       logData: VcsLogData,
                                       detail: VcsFullCommitDetails,
                                       parent: Disposable): CommitDetailsListPanel {
    val detailsPanel = CommitDetailsListPanel(project, parent) {
      CommitDetailsPanel { commitId -> VcsProjectLog.showRevisionInMainLog(project, commitId.root, commitId.hash) }
    }
    val colorManager = VcsLogColorManagerFactory.create(logData.logProviders.keys)
    val listener = VcsLogCommitSelectionListenerForDetails(logData, colorManager, detailsPanel, parent)
    listener.onDetailsLoaded(listOf(logData.storage.getCommitIndex(detail.id, detail.root)), listOf(detail))
    return detailsPanel
  }

  companion object {
    private const val DETAILS_PROPORTION = 0.7f
  }
}
