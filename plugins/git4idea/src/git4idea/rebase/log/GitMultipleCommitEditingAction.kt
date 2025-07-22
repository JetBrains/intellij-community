// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogUiEx
import git4idea.repo.GitRepository

internal abstract class GitMultipleCommitEditingAction : GitCommitEditingActionBase<GitCommitEditingActionBase.MultipleCommitEditingData>() {
  override fun createCommitEditingData(
    repository: GitRepository,
    selection: VcsLogCommitSelection,
    logData: VcsLogData,
    logUiEx: VcsLogUiEx?,
    selectedChanges: List<Change>,
  ) = CommitEditingDataCreationResult.Created(MultipleCommitEditingData(repository, selection, logData, logUiEx))
}