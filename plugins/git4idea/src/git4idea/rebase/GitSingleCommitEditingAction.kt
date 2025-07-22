// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogUiEx
import git4idea.GitUtil
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase
import git4idea.repo.GitRepository

internal abstract class GitSingleCommitEditingAction : GitCommitEditingActionBase<GitSingleCommitEditingAction.SingleCommitEditingData>() {
  override fun createCommitEditingData(
    repository: GitRepository,
    selection: VcsLogCommitSelection,
    logData: VcsLogData,
    logUiEx: VcsLogUiEx?,
    selectedChanges: List<Change>,
  ): CommitEditingDataCreationResult<SingleCommitEditingData> {
    if (selection.commits.size != 1) {
      return CommitEditingDataCreationResult.Prohibited()
    }
    return CommitEditingDataCreationResult.Created(SingleCommitEditingData(repository, selection, logData, logUiEx, selectedChanges))
  }

  override fun lastCheckCommitsEditingAvailability(commitEditingData: SingleCommitEditingData): String? {
    val commit = commitEditingData.selectedCommit
    val branches = findContainingBranches(commitEditingData.logData, commit.root, commit.id)

    if (GitUtil.HEAD !in branches) {
      return GitBundle.message("rebase.log.commit.editing.action.commit.not.in.head.error.text")
    }

    // and not if pushed to a protected branch
    val protectedBranch = findProtectedRemoteBranch(commitEditingData.repository, branches)
    if (protectedBranch != null) {
      return GitBundle.message("rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text", protectedBranch)
    }
    return null
  }

  class SingleCommitEditingData(
    repository: GitRepository,
    selection: VcsLogCommitSelection,
    logData: VcsLogData,
    logUiEx: VcsLogUiEx?,
    val selectedChanges: List<Change>,
  ) : MultipleCommitEditingData(repository, selection, logData, logUiEx) {
    val selectedCommit = selection.cachedMetadata.first()
    val selectedCommitFullDetails = selection.cachedFullDetails.first()
    val isHeadCommit = selectedCommit.id.asString() == repository.currentRevision
  }
}
