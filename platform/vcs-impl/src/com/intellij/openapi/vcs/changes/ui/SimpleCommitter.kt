// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.util.NullableFunction
import com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress

open class SimpleCommitter(
  project: Project,
  changes: List<Change>,
  commitMessage: String,
  handlers: List<CheckinHandler>,
  additionalData: NullableFunction<Any, Any>,
  private val localHistoryActionName: String = "Commit Changes"
) : AbstractCommitter(project, changes, commitMessage, handlers, additionalData) {

  private var myAction = LocalHistoryAction.NULL

  override fun commit() {
    processChangesByVcs(project, changes, this::commit)
  }

  override fun afterCommit() {
    ChangeListManagerImpl.getInstanceImpl(project).showLocalChangesInvalidated()

    myAction = runReadAction { LocalHistory.getInstance().startAction(localHistoryActionName) }
  }

  override fun onSuccess() = Unit

  override fun onFailure() = Unit

  override fun onFinish() {
    refreshChanges()
    runOrInvokeLaterAboveProgress(this::doPostRefresh, null, project)
  }

  protected open fun afterRefreshChanges() {
    val cache = CommittedChangesCache.getInstance(project)
    // in background since commit must have authorized
    cache.refreshAllCachesAsync(false, true)
    cache.refreshIncomingChangesAsync()
  }

  private fun refreshChanges() {
    val toRefresh = mutableListOf<Change>()
    processChangesByVcs(project, changes) { vcs, changes ->
      val environment = vcs.checkinEnvironment
      if (environment != null && environment.isRefreshAfterCommitNeeded) {
        toRefresh.addAll(changes)
      }
    }

    if (!toRefresh.isEmpty()) {
      ProgressManager.progress(VcsBundle.message("commit.dialog.refresh.files"))
      RefreshVFsSynchronously.updateChanges(toRefresh)
    }
  }

  private fun doPostRefresh() {
    myAction.finish()
    if (!project.isDisposed) {
      // after vcs refresh is completed, outdated notifiers should be removed if some exists...
      VcsDirtyScopeManager.getInstance(project).filePathsDirty(pathsToRefresh, null)
      ChangeListManager.getInstance(project).invokeAfterUpdate(this::afterRefreshChanges, InvokeAfterUpdateMode.SILENT, null, null)

      LocalHistory.getInstance().putSystemLabel(project, "$localHistoryActionName: $commitMessage")
    }
  }
}