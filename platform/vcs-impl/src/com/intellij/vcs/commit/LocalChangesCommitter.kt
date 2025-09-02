// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.VcsActivity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private val FRESH_ROOTS_KEY = Key.create<Set<VirtualFile>>("Git.Commit.FreshRoots")
var CommitContext.freshRoots: Set<VirtualFile>? by commitProperty(FRESH_ROOTS_KEY, null)

private val COMMIT_WITHOUT_CHANGES_ROOTS_KEY = Key.create<Collection<VcsRoot>>("Vcs.Commit.CommitWithoutChangesRoots")
var CommitContext.commitWithoutChangesRoots: Collection<VcsRoot> by commitProperty(COMMIT_WITHOUT_CHANGES_ROOTS_KEY, emptyList())

open class LocalChangesCommitter(
  project: Project,
  val commitState: ChangeListCommitState,
  commitContext: CommitContext,
  private val localHistoryActionName: @Nls String = VcsBundle.message("activity.name.commit")
) : VcsCommitter(project, commitState.changes, commitState.commitMessage, commitContext, true) {

  init {
    addResultHandler(CommittedChangesCacheListener(project))
    addResultHandler(ChangeListDataCleaner(this))
    addResultHandler(EmptyChangeListDeleter(this))
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

      val toRefresh = mutableListOf<Change>()
      val additionalPathsToRefresh = mutableListOf<FilePath>()

      processChangesByVcs(project, changes) { vcs, changes ->
        val environment = vcs.checkinEnvironment
        if (environment != null && environment.isRefreshAfterCommitNeeded) {
          toRefresh.addAll(changes)
        }

        additionalPathsToRefresh.addAll(VcsPathsToRefreshProvider.collectPathsToRefreshForVcs(project, vcs))
      }

      if (toRefresh.isNotEmpty()) {
        progress(VcsBundle.message("commit.dialog.refresh.files"))
        RefreshVFsSynchronously.updateChanges(toRefresh)
      }

      refreshAction.finish()

      val allPathsToRefresh = pathsToRefresh + additionalPathsToRefresh
      if (allPathsToRefresh.isNotEmpty()) {
        ChangeListManagerImpl.getInstanceImpl(project).showLocalChangesInvalidated()
      }

      VcsDirtyScopeManager.getInstance(project).filePathsDirty(allPathsToRefresh, null)

      LocalHistory.getInstance().putEventLabel(project, getLocalHistoryEventName(commitContext, commitMessage), VcsActivity.Commit)
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

private class EmptyChangeListDeleter(val committer: LocalChangesCommitter) : CommitterResultHandler {
  override fun onAfterRefresh() {
    if (committer.isSuccess) {
      val changeListManager = ChangeListManager.getInstance(committer.project)
      val listName = committer.commitState.changeList.name
      val localList = changeListManager.findChangeList(listName) ?: return

      if (!localList.isDefault) {
        changeListManager.scheduleAutomaticEmptyChangeListDeletion(localList)
      }
    }
  }
}

private class ChangeListDataCleaner(val committer: LocalChangesCommitter) : CommitterResultHandler {
  override fun onAfterRefresh() {
    if (committer.isSuccess) {
      val changeListManager = ChangeListManagerEx.getInstanceEx(committer.project)
      val listName = committer.commitState.changeList.name
      changeListManager.editChangeListData(listName, null)
    }
  }
}

@ApiStatus.Internal
fun getLocalHistoryEventName(commitContext: CommitContext, commitMessage: String): @NlsContexts.Label String {
  if (commitContext.isAmendCommitMode) return VcsBundle.message("activity.name.amend.message", commitMessage)
  return VcsBundle.message("activity.name.commit.message", commitMessage)
}
