// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
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
import com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress
import org.jetbrains.annotations.Nls

private val COMMIT_WITHOUT_CHANGES_ROOTS_KEY = Key.create<Collection<VcsRoot>>("Vcs.Commit.CommitWithoutChangesRoots")
var CommitContext.commitWithoutChangesRoots: Collection<VcsRoot> by commitProperty(COMMIT_WITHOUT_CHANGES_ROOTS_KEY, emptyList())

open class LocalChangesCommitter(
  project: Project,
  changes: List<Change>,
  commitMessage: String,
  commitContext: CommitContext,
  private val localHistoryActionName: @Nls String = message("commit.changes")
) : AbstractCommitter(project, changes, commitMessage, commitContext) {

  private var myAction = LocalHistoryAction.NULL

  protected var isSuccess = false

  override fun commit() {
    val committedVcses = commitChanges()
    commitWithoutChanges(committedVcses)
  }

  private fun commitChanges(): Set<AbstractVcs> {
    val committedVcses = mutableSetOf<AbstractVcs>()
    processChangesByVcs(project, changes) { vcs, vcsChanges ->
      committedVcses += vcs
      commit(vcs, vcsChanges)
    }
    return committedVcses
  }

  private fun commitWithoutChanges(committedVcses: Set<AbstractVcs>) {
    val commitWithoutChangesVcses = commitContext.commitWithoutChangesRoots.mapNotNullTo(mutableSetOf()) { it.vcs }
    (commitWithoutChangesVcses - committedVcses).forEach { commit(it, emptyList()) }
  }

  override fun afterCommit() {
    if (pathsToRefresh.isNotEmpty()) {
      ChangeListManagerImpl.getInstanceImpl(project).showLocalChangesInvalidated()
    }

    myAction = runReadAction { LocalHistory.getInstance().startAction(localHistoryActionName) }
  }

  override fun onSuccess() {
    isSuccess = true
  }

  override fun onFailure() = Unit

  override fun onFinish() {
    refreshChanges()
    runOrInvokeLaterAboveProgress({ doPostRefresh() }, null, project)
  }

  private fun refreshChanges() {
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
  }

  private fun doPostRefresh() {
    myAction.finish()
    if (!project.isDisposed) {
      // after vcs refresh is completed, outdated notifiers should be removed if some exists...
      VcsDirtyScopeManager.getInstance(project).filePathsDirty(pathsToRefresh, null)
      ChangeListManager.getInstance(project).invokeAfterUpdate(true) { afterRefreshChanges() }

      LocalHistory.getInstance().putSystemLabel(project, "$localHistoryActionName: $commitMessage")
    }
  }

  protected open fun afterRefreshChanges() {
    val cache = CommittedChangesCache.getInstance(project)
    // in background since commit must have authorized
    cache.refreshAllCachesAsync(false, true)
    cache.refreshIncomingChangesAsync()
  }
}