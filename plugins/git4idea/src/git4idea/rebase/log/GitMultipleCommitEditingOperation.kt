// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import git4idea.branch.GitRebaseParams
import git4idea.rebase.*
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

internal open class GitMultipleCommitEditingOperation(protected val repository: GitRepository) {
  protected val project = repository.project

  protected fun execute(commits: List<VcsCommitMetadata>, rebaseEditor: GitRebaseEditorHandler) {
    val base = commits.last().parents.first().asString()
    val params = GitRebaseParams.editCommits(
      repository.vcs.version,
      base,
      rebaseEditor,
      false,
      GitRebaseParams.AutoSquashOption.DISABLE
    )
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(project, params, listOf(repository), indicator)
    val rewordProcess = GitRebaseProcess(project, spec, null)
    rewordProcess.rebase()
  }

  protected abstract class GitMultipleCommitEditingEditorHandler(
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
}

