// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel.ChangelistData
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus

/** Shows the changes of an already loaded commit in a changes browser with the commit details panel of the VCS Log under it. */
@ApiStatus.Experimental
object CommitChangesViewer {
  /** Part of the panel height that the changes browser gets; the commit details get the rest. */
  private const val DETAILS_PROPORTION = 0.7f

  /**
   * Shows the changes of [detail] in a changes browser: the tree of the affected files, with the commit details panel of the VCS Log
   * under it. The details part is the panel that the log tool window and the file history panel use: the hash, the author, the date,
   * the branches, the tags and the external statuses, with links that navigate to the main log.
   *
   * The commit must be loaded already: the panel shows it at once and loads no more commit data.
   * The changes themselves are loaded in the background.
   *
   * The changes browser opens as a tab of the Commit tool window, or as a dialog if the IDE is set up so.
   * It owns the panel it shows, so the caller has nothing to dispose.
   *
   * @param fileToSelect the file to select in the changes tree once it is loaded. Pass null to select nothing.
   */
  @RequiresEdt
  @JvmStatic
  @JvmOverloads
  fun showCommitChanges(project: Project, detail: VcsFullCommitDetails, fileToSelect: FilePath? = null) {
    val logData = VcsProjectLog.getInstance(project).dataManager
    val panel = LoadingCommittedChangeListPanel(project) { parent ->
      logData?.let { createCommitDetailsComponent(project, it, detail, parent) }
    }
    panel.setCommitDetailsProportion(DETAILS_PROPORTION)
    panel.loadChangesInBackground { ChangelistData(VcsLogUtil.createCommittedChangeList(detail), fileToSelect) }

    val title = VcsLogBundle.message("dialog.title.paths.affected.by.commit", detail.id.toShortString())
    ChangeListViewerDialog.show(project, title, panel)
  }

  /**
   * @param parent disposes the panel and the selection listener that feeds it
   */
  @RequiresEdt
  private fun createCommitDetailsComponent(
    project: Project,
    logData: VcsLogData,
    detail: VcsFullCommitDetails,
    parent: Disposable,
  ): CommitDetailsListPanel {
    val detailsPanel = CommitDetailsListPanel(project, parent) {
      CommitDetailsPanel { commitId -> VcsProjectLog.showRevisionInMainLog(project, commitId.root, commitId.hash) }
    }
    val colorManager = VcsLogColorManagerFactory.create(logData.logProviders.keys)
    val listener = VcsLogCommitSelectionListenerForDetails(logData, colorManager, detailsPanel, parent)
    listener.onDetailsLoaded(listOf(logData.storage.getCommitIndex(detail.id, detail.root)), listOf(detail))
    return detailsPanel
  }
}
