// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitUtil
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase
import git4idea.repo.GitRepository

internal abstract class GitSingleCommitEditingAction : GitCommitEditingActionBase<GitSingleCommitEditingAction.SingleCommitEditingData>() {
  override fun createCommitEditingData(
    repository: GitRepository,
    log: VcsLog,
    logData: VcsLogData,
    logUi: VcsLogUi
  ): CommitEditingDataCreationResult<SingleCommitEditingData> {
    if (log.selectedCommits.size != 1) {
      return CommitEditingDataCreationResult.Prohibited()
    }
    return CommitEditingDataCreationResult.Created(SingleCommitEditingData(repository, log, logData, logUi))
  }

  override fun checkCommitsEditingAvailability(commitEditingData: SingleCommitEditingData): String? {
    val commit = commitEditingData.selectedCommit
    val branches = findContainingBranches(commitEditingData.logData, commit.root, commit.id)

    if (GitUtil.HEAD !in branches) {
      return GitBundle.getString("rebase.log.commit.editing.action.commit.not.in.head.error.text")
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
    log: VcsLog,
    logData: VcsLogData,
    logUi: VcsLogUi
  ) : MultipleCommitEditingData(repository, log, logData, logUi) {
    val selectedCommit: VcsShortCommitDetails = selectedCommitList.first()
    val isHeadCommit = selectedCommit.id.asString() == repository.currentRevision
  }
}
