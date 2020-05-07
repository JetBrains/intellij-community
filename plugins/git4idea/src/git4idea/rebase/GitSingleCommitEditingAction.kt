// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import git4idea.rebase.log.GitMultipleCommitEditingActionBase
import git4idea.repo.GitRepository

abstract class GitSingleCommitEditingAction : GitMultipleCommitEditingActionBase<GitSingleCommitEditingAction.SingleCommitEditingData>() {
  override fun createCommitEditingData(
    repository: GitRepository,
    log: VcsLog,
    logData: VcsLogData,
    logUi: VcsLogUi
  ): SingleCommitEditingData? {
    if (log.selectedCommits.size != 1) {
      return null
    }
    return SingleCommitEditingData(repository, log, logData, logUi)
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
