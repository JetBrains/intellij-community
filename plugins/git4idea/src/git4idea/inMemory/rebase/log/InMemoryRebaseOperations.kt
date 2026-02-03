// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import git4idea.inMemory.rebase.performInMemoryRebase
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.convertToModel
import git4idea.rebase.interactive.tryGetEntriesUsingLog
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.indicesByPredicate
import git4idea.repo.GitRepository

internal object InMemoryRebaseOperations {
  private val LOG = logger<InMemoryRebaseOperations>()

  suspend fun squash(repository: GitRepository, logData: VcsLogData, commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult {
    return executeInMemoryCommitModification(repository, logData, commitsToSquash) { model, toSquashIndices ->
      val uniteRoot = model.unite(toSquashIndices)
      model.reword(uniteRoot.index, newMessage)
    }
  }

  suspend fun drop(repository: GitRepository, logData: VcsLogData, commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
    return executeInMemoryCommitModification(repository, logData, commitsToDrop) { model, toDropIndices ->
      model.drop(toDropIndices)
    }
  }

  private suspend fun executeInMemoryCommitModification(
    repository: GitRepository,
    logData: VcsLogData,
    commits: List<VcsCommitMetadata>,
    modelModifier: (GitRebaseTodoModel<out GitRebaseEntryWithDetails>, List<Int>) -> Unit,
  ): GitCommitEditingOperationResult {
    val generatedEntries = tryGetEntriesUsingLog(repository, commits.last(), logData)
                           ?: return GitCommitEditingOperationResult.Incomplete

    val model = convertToModel(generatedEntries)
    val commitHashes = commits.map { commit -> commit.id.asString() }.toSet()
    val targetIndices = model.elements.indicesByPredicate {
      it.entry.commitDetails.id.asString() in commitHashes
    }

    if (targetIndices.size != commits.size) {
      LOG.warn("Couldn't find all commits in the model")
      return GitCommitEditingOperationResult.Incomplete
    }

    modelModifier(model, targetIndices)

    return performInMemoryRebase(repository, generatedEntries, model, notifySuccess = false)
  }
}