// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.squash

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.chainCommits
import git4idea.inMemory.objects.Oid
import git4idea.inMemory.rebase.log.GitInMemoryCommitEditingOperation
import git4idea.rebase.GitRebaseUtils
import org.jetbrains.annotations.NonNls

internal class GitInMemorySquashConsecutiveOperation(
  objectRepo: GitObjectRepository,
  private val commitsToSquash: List<VcsCommitMetadata>,
  private val newMessage: String,
) : GitInMemoryCommitEditingOperation(objectRepo, commitsToSquash.last()) {

  init {
    require(GitRebaseUtils.areConsecutiveCommits(commitsToSquash)) {
      "Provided commits are not consecutive"
    }
  }

  @NonNls
  override val reflogMessage: String = "squash"
  override val failureTitle: String = GitBundle.message("in.memory.rebase.log.squash.failed.title")

  override suspend fun editCommits(): CommitEditingResult {
    val newestCommitToSquash = baseToHeadCommitsRange[commitsToSquash.size - 1]

    val squashRangeParent = baseToHeadCommitsRange.first().parentsOids.single()
    val squashedCommit = objectRepo.commitTreeWithOverrides(
      newestCommitToSquash,
      message = newMessage.toByteArray(),
      parentsOids = listOf(squashRangeParent)
    )

    val commitsAfterSquashRange = baseToHeadCommitsRange.subList(commitsToSquash.size, baseToHeadCommitsRange.size)
    val newHead = objectRepo.chainCommits(squashedCommit, commitsAfterSquashRange)

    return CommitEditingResult(newHead, squashedCommit)
  }
}