// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import org.jetbrains.annotations.Nls

private val COMMIT_WITHOUT_CHANGES_ROOTS_KEY = Key.create<Collection<VcsRoot>>("Vcs.Commit.CommitWithoutChangesRoots")
var CommitContext.commitWithoutChangesRoots: Collection<VcsRoot> by commitProperty(COMMIT_WITHOUT_CHANGES_ROOTS_KEY, emptyList())

open class LocalChangesCommitter(
  project: Project,
  val commitState: ChangeListCommitState,
  commitContext: CommitContext,
  private val localHistoryActionName: @Nls String = message("commit.changes")
) : VcsCommitter(project, commitState.changes, commitState.commitMessage, commitContext, true) {

  init {
    addResultHandler(CommittedChangesCacheListener(project))
    addResultHandler(ChangeListDataCleaner(this))
  }

  var isSuccess = false
    private set

  override fun commit() {
    try {
      vetoDocumentSaving(project, changes) {
        val committedVcses = commitChanges()
        commitWithoutChanges(committedVcses)
      }

      isSuccess = commitErrors.isEmpty()
    }
    finally {
      refreshChanges()
    }
  }

  private fun commitChanges(): Set<AbstractVcs> {
    val committedVcses = mutableSetOf<AbstractVcs>()
    processChangesByVcs(project, changes) { vcs, vcsChanges ->
      committedVcses += vcs
      vcsCommit(vcs, vcsChanges)
    }
    return committedVcses
  }

  private fun commitWithoutChanges(committedVcses: Set<AbstractVcs>) {
    val commitWithoutChangesVcses = commitContext.commitWithoutChangesRoots.mapNotNullTo(mutableSetOf()) { it.vcs }
    (commitWithoutChangesVcses - committedVcses).forEach { vcsCommit(it, emptyList()) }
  }

  private fun refreshChanges() {
    try {
      val refreshAction = runReadAction { LocalHistory.getInstance().startAction(localHistoryActionName) }

      if (pathsToRefresh.isNotEmpty()) {
        ChangeListManagerImpl.getInstanceImpl(project).showLocalChangesInvalidated()
      }

      val toRefresh = mutableListOf<Change>()
      processChangesByVcs(project, changes) { vcs, changes ->
        val environment = vcs.checkinEnvironment
        if (environment != null && environment.isRefreshAfterCommitNeeded) {
          toRefresh.addAll(changes)
        }
      }

      if (toRefresh.isNotEmpty()) {
        progress(message("commit.dialog.refresh.files"))
        RefreshVFsSynchronously.updateChanges(toRefresh)
      }

      refreshAction.finish()

      VcsDirtyScopeManager.getInstance(project).filePathsDirty(pathsToRefresh, null)

      LocalHistory.getInstance().putSystemLabel(project, "$localHistoryActionName: $commitMessage")
    }
    finally {
      ChangeListManager.getInstance(project).invokeAfterUpdate(true) { fireAfterRefresh() }
    }
  }
}

private class CommittedChangesCacheListener(val project: Project) : CommitterResultHandler {
  override fun onAfterRefresh() {
    val cache = CommittedChangesCache.getInstance(project)
    // in background since commit must have authorized
    cache.refreshAllCachesAsync(false, true)
    cache.refreshIncomingChangesAsync()
  }
}

private class ChangeListDataCleaner(val committer: LocalChangesCommitter) : CommitterResultHandler {
  override fun onAfterRefresh() {
    if (committer.isSuccess) {
      ChangeListManagerEx.getInstanceEx(committer.project).editChangeListData(committer.commitState.changeList.name, null)
    }
  }
}