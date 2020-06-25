// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.data.VcsLogData
import git4idea.repo.GitRepository

internal abstract class GitMultipleCommitEditingAction : GitCommitEditingActionBase<GitCommitEditingActionBase.MultipleCommitEditingData>() {
  override fun createCommitEditingData(
    repository: GitRepository,
    log: VcsLog,
    logData: VcsLogData,
    logUi: VcsLogUi
  ) = CommitEditingDataCreationResult.Created(MultipleCommitEditingData(repository, log, logData, logUi))
}