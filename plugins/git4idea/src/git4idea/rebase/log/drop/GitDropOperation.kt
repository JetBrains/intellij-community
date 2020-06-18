// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.drop

import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.log.GitMultipleCommitEditingEditorHandler
import git4idea.rebase.log.GitMultipleCommitEditingOperation
import git4idea.repo.GitRepository

internal class GitDropOperation(repository: GitRepository) : GitMultipleCommitEditingOperation(repository) {
  fun execute(commitsToDrop: List<VcsCommitMetadata>) {
    val rebaseEditor = DropRebaseEditorHandler(repository, commitsToDrop)
    rebase(commitsToDrop, rebaseEditor)
  }

  private class DropRebaseEditorHandler(
    repository: GitRepository,
    commitsToDrop: List<VcsShortCommitDetails>
  ) : GitMultipleCommitEditingEditorHandler(repository, commitsToDrop) {
    override fun processModel(commitIndices: List<Int>, model: GitRebaseTodoModel<GitRebaseEntry>) {
      model.drop(commitIndices)
    }
  }
}