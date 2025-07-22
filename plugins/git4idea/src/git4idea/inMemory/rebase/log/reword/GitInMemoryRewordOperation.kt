// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import com.intellij.openapi.diagnostic.logger
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.chainCommits
import git4idea.inMemory.rebase.log.GitInMemoryCommitEditingOperation
import org.jetbrains.annotations.NonNls

internal class GitInMemoryRewordOperation(
  objectRepo: GitObjectRepository,
  targetCommitMetadata: VcsCommitMetadata,
  private val newMessage: String,
) : GitInMemoryCommitEditingOperation(objectRepo, targetCommitMetadata) {
  companion object {
    private val LOG = logger<GitInMemoryRewordOperation>()
  }

  @NonNls
  override val reflogMessage: String = "reword $targetCommitMetadata"
  override val failureTitle: String = GitBundle.message("in.memory.rebase.log.reword.failed.title")

  override suspend fun editCommits(): CommitEditingResult {
    val targetCommit = commits.first()

    LOG.info("Start computing new head for reword operation of $targetCommit")
    val rewordedTargetCommit = objectRepo.commitTreeWithOverrides(commits.first(), message = newMessage.toByteArray())
    val newHead = objectRepo.chainCommits(rewordedTargetCommit, commits.drop(1))

    LOG.info("Finish computing new head for reword operation")
    return CommitEditingResult(newHead, rewordedTargetCommit)
  }
}