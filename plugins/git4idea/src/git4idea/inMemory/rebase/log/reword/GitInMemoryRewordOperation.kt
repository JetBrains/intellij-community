// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.rebase.interactive.getRebaseUpstreamFor
import git4idea.rebase.log.GitCommitEditingOperation
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NonNls

internal class GitInMemoryRewordOperation(
  repository: GitRepository,
  val objectRepo: GitObjectRepository,
  private val commit: VcsCommitMetadata,
  private val newMessage: String,
) : GitCommitEditingOperation(repository) {
  companion object {
    private val LOG = logger<GitInMemoryRewordOperation>()
    @NonNls
    private const val REFLOG_MESSAGE_SUFFIX = "by IntelliJ Git plugin"
  }

  private lateinit var initialHeadPosition: String

  suspend fun execute(): GitCommitEditingOperationResult {
    LOG.info("Starting in-memory reword operation for commit: ${commit.id}")
    repository.update()
    initialHeadPosition = repository.currentRevision!!
    LOG.debug("current head: $initialHeadPosition")

    try {
      val commits = findCommitsRange()
      LOG.debug("Found ${commits.size} commits in range to rebuild")

      val newHead = buildNewCommits(commits)
      updateHead(newHead.hex())
      repository.update()
      val upstream = getRebaseUpstreamFor(commit)

      LOG.info("Successfully completed reword operation")
      return GitCommitEditingOperationResult.Complete(repository, upstream, initialHeadPosition,
                                                      newHead.hex())
    }
    catch (e: VcsException) {
      notifyOperationFailed(e)
      return GitCommitEditingOperationResult.Incomplete
    }
  }

  private fun updateHead(newHead: String) {
    val refLogMessage = "reword ${commit.id} $REFLOG_MESSAGE_SUFFIX"
    val handler = GitLineHandler(project, repository.root, GitCommand.UPDATE_REF).apply {
      addParameters(GitUtil.HEAD, newHead)
      addParameters("-m", refLogMessage)
    }

    Git.getInstance().runCommand(handler).throwOnError()
  }

  private fun findCommitsRange(): List<GitObject.Commit> {
    var currentCommit = objectRepo.findCommit(Oid.fromHex(initialHeadPosition))
    val commits = mutableListOf(currentCommit)

    val commitOid = Oid.fromHash(commit.id)
    while (currentCommit.oid != commitOid) {
      currentCommit = objectRepo.findCommit(currentCommit.parentsOids.single())
      commits.add(currentCommit)
    }
    return commits.reversed()
  }

  private suspend fun buildNewCommits(commits: List<GitObject.Commit>): Oid {
    var currentNewCommit = objectRepo.commitTreeWithOverrides(commits.first(), message = newMessage.toByteArray())

    reportRawProgress { reporter ->
      reporter.fraction(1 / commits.size.toDouble())

      for ((i, commit) in commits.drop(1).withIndex()) {
        currentNewCommit = objectRepo.commitTreeWithOverrides(commit, parentsOids = listOf(currentNewCommit))
        LOG.debug("New commit ${currentNewCommit.hex()} created")

        reporter.fraction((i + 1) / commits.size.toDouble())
      }
    }
    return currentNewCommit
  }

  private fun notifyOperationFailed(exception: VcsException) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.IN_MEMORY_OPERATION_FAILED,
      GitBundle.message("in.memory.rebase.log.reword.failed.title"),
      exception.message
    )
  }
}