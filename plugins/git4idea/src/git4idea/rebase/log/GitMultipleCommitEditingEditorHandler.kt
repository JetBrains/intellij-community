// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.vcs.log.VcsShortCommitDetails
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.convertToEntries
import git4idea.rebase.interactive.convertToModel
import git4idea.repo.GitRepository

internal fun <T> List<T>.indicesByPredicate(predicate: (T) -> Boolean): List<Int> = this.mapIndexedNotNull { index, element ->
  if (predicate(element)) {
    index
  }
  else {
    null
  }
}

internal abstract class GitMultipleCommitEditingEditorHandler(
  protected val repository: GitRepository,
  private val commits: List<VcsShortCommitDetails>
) : GitInteractiveRebaseEditorHandler(repository.project, repository.root) {
  final override fun collectNewEntries(entries: List<GitRebaseEntry>): List<GitRebaseEntry> {
    val model = convertToModel(entries)
    val hashes = commits.map { it.id.asString() }
    val indices = entries.indicesByPredicate { gitRebaseEntry ->
      // we can use trie to speed it up, but we assume that there weren't selected so much commits
      hashes.any { it.startsWith(gitRebaseEntry.commit) }
    }
    processModel(indices, model)
    return model.convertToEntries()
  }

  abstract fun processModel(commitIndices: List<Int>, model: GitRebaseTodoModel<GitRebaseEntry>)
}
