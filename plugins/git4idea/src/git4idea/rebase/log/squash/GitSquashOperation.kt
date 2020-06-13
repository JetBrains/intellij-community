// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.squash

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.log.GitMultipleCommitEditingOperation
import git4idea.repo.GitRepository

internal class GitSquashOperation(repository: GitRepository) : GitMultipleCommitEditingOperation(repository) {
  fun execute(commitsToSquash: List<VcsCommitMetadata>, newMessage: String) {
    val rebaseEditor = SquashRebaseEditorHandler(repository, commitsToSquash, newMessage)
    execute(commitsToSquash, rebaseEditor)
  }

  private class SquashRebaseEditorHandler(
    repository: GitRepository,
    val commitsToSquash: List<VcsCommitMetadata>,
    val newMessage: String
  ) : GitMultipleCommitEditingEditorHandler(repository, commitsToSquash) {
    override fun processModel(commitIndices: List<Int>, model: GitRebaseTodoModel<GitRebaseEntry>) {
      val uniteRoot = model.unite(commitIndices)
      model.reword(uniteRoot.index, newMessage)
      processModel(model) { entry ->
        commitsToSquash.find { it.id.asString().startsWith(entry.commit) }?.fullMessage
        ?: throw IllegalStateException("Full message should be taken from reworded commits only")
      }
    }
  }
}