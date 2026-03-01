// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.rebase.performInMemoryRebase
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.convertToModel
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.rebase.log.indicesByPredicate
import git4idea.repo.GitRepository

internal sealed interface RebaseEntriesSource {
  data class Entries(val entries: List<GitRebaseEntry>) : RebaseEntriesSource
  data class LogData(val logData: VcsLogData) : RebaseEntriesSource
}

internal object InMemoryRebaseOperations {
  private val LOG = logger<InMemoryRebaseOperations>()

  suspend fun squash(
    repository: GitRepository,
    commitsToSquash: List<VcsCommitMetadata>,
    newMessage: String,
    entriesSource: RebaseEntriesSource,
  ): GitCommitEditingOperationResult {
    return executeInMemoryCommitModification(repository, commitsToSquash, entriesSource) { model, toSquashIndices ->
      val uniteRoot = model.unite(toSquashIndices)
      model.reword(uniteRoot.index, newMessage)
    }
  }

  suspend fun drop(
    repository: GitRepository,
    commitsToDrop: List<VcsCommitMetadata>,
    entriesSource: RebaseEntriesSource,
  ): GitCommitEditingOperationResult {
    return executeInMemoryCommitModification(repository, commitsToDrop, entriesSource) { model, toDropIndices ->
      model.drop(toDropIndices)
    }
  }

  private suspend fun executeInMemoryCommitModification(
    repository: GitRepository,
    commits: List<VcsCommitMetadata>,
    entriesSource: RebaseEntriesSource,
    modelModifier: (GitRebaseTodoModel<out GitRebaseEntry>, List<Int>) -> Unit,
  ): GitCommitEditingOperationResult {
    val generatedEntries = when (entriesSource) {
                             is RebaseEntriesSource.Entries -> entriesSource.entries
                             is RebaseEntriesSource.LogData -> {
                               repository.project.service<GitInteractiveRebaseEntriesProvider>().tryGetEntriesUsingLog(repository, commits.last(), entriesSource.logData)
                             }
                           } ?: return GitCommitEditingOperationResult.Incomplete

    val model = convertToModel(generatedEntries)
    val commitHashes = commits.map { commit -> commit.id.asString() }.toSet()
    val targetIndices = model.elements.indicesByPredicate { element ->
      element.entry.commit in commitHashes
    }

    if (targetIndices.size != commits.size) {
      LOG.warn("Couldn't find all commits in the model")
      return GitCommitEditingOperationResult.Incomplete
    }

    modelModifier(model, targetIndices)

    val objectRepo = GitObjectRepository(repository)
    return performInMemoryRebase(objectRepo, generatedEntries, model, notifySuccess = false)
  }
}