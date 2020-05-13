// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.squash

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseProcess
import git4idea.rebase.GitRebaseSpec
import git4idea.rebase.interactive.convertToEntries
import git4idea.rebase.interactive.convertToModel
import git4idea.repo.GitRepository

class GitSquashOperation(private val repository: GitRepository) {
  private val project = repository.project

  init {
    repository.update()
  }

  fun execute(commitsToSquash: List<VcsCommitMetadata>, newMessage: String) {
    val base = commitsToSquash.last().parents.first().asString()
    val rebaseEditor = SquashRebaseEditorHandler(repository, commitsToSquash, newMessage)
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

  private class SquashRebaseEditorHandler(
    repository: GitRepository,
    val commitsToSquash: List<VcsCommitMetadata>,
    val newMessage: String
  ) : GitInteractiveRebaseEditorHandler(repository.project, repository.root) {
    override fun collectNewEntries(entries: List<GitRebaseEntry>): List<GitRebaseEntry> {
      val model = convertToModel(entries)
      val hashesToSquash = commitsToSquash.map { it.id.asString() }
      val indicesToSquash = entries.indicesByPredicate { gitRebaseEntry ->
        // we can use trie to speed it up, but we assume that there weren't selected so much commits
        hashesToSquash.any { it.startsWith(gitRebaseEntry.commit) }
      }
      val uniteRoot = model.unite(indicesToSquash)
      model.reword(uniteRoot.index, newMessage)
      processModel(model) { entry ->
        commitsToSquash.find { it.id.asString().startsWith(entry.commit) }?.fullMessage
        ?: throw IllegalStateException("Full message should be taken from reworded commits only")
      }
      return model.convertToEntries()
    }

    private fun <T> List<T>.indicesByPredicate(predicate: (T) -> Boolean): List<Int> = this.mapIndexedNotNull { index, element ->
      if (predicate(element)) {
        index
      }
      else {
        null
      }
    }
  }
}