// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.PostCommitChangeConverter
import com.intellij.util.CollectConsumer
import com.intellij.vcs.commit.commitExecutorProperty
import com.intellij.vcs.log.Hash
import git4idea.GitCommit
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.history.GitCommitRequirements
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository

class GitPostCommitChangeConverter(private val project: Project) : PostCommitChangeConverter {
  override fun collectChangesAfterCommit(commitContext: CommitContext): List<Change> {
    val hashes = commitContext.postCommitHashes ?: return emptyList()

    val result = mutableListOf<Change>()
    for ((repo, hash) in hashes) {
      result += loadChangesFromCommit(repo, hash)
    }
    return result
  }

  private fun loadChangesFromCommit(repo: GitRepository, hash: Hash): List<Change> {
    val consumer = CollectConsumer<GitCommit>()
    val commitRequirements = GitCommitRequirements(diffInMergeCommits = GitCommitRequirements.DiffInMergeCommits.FIRST_PARENT)
    GitLogUtil.readFullDetailsForHashes(project, repo.root, listOf(hash.asString()), commitRequirements, consumer)
    val commit = consumer.result.first()

    return commit.getChanges(0).toList()
  }

  override fun areConsequentCommits(commitContexts: List<CommitContext>): Boolean {
    val commitHashes = commitContexts.map { it.postCommitHashes }
    if (commitHashes.all { it == null }) return true // no git commits, not our problem
    if (commitHashes.any { it == null }) return false // has non-git commits, give up

    val repoMap = mutableMapOf<GitRepository, Hash>()
    for (hashes in commitHashes.reversed()) {
      for ((repo, hash) in hashes!!) {
        val oldHash = repoMap.put(repo, hash)
        if (oldHash != null) {
          val parentHash = Git.getInstance().resolveReference(repo, "${oldHash}^1")
          if (parentHash != hash) {
            logger<GitPostCommitChangeConverter>().debug("Non-consequent commits: $oldHash - $hash")
            return false
          }
        }
      }
    }
    return true
  }

  override fun isFailureUpToDate(commitContexts: List<CommitContext>): Boolean {
    val lastCommit = commitContexts.lastOrNull()?.postCommitHashes ?: return true // non-git commits, not our problem
    return lastCommit.entries.any { (repo, hash) -> repo.currentRevision == hash.asString() }
  }

  companion object {
    private val GIT_POST_COMMIT_HASHES_KEY = Key.create<MutableMap<GitRepository, Hash>>("Git.Post.Commit.Hash")

    private var CommitContext.postCommitHashes: MutableMap<GitRepository, Hash>? by commitExecutorProperty(GIT_POST_COMMIT_HASHES_KEY, null)

    @JvmStatic
    fun markRepositoryCommit(commitContext: CommitContext, repository: GitRepository) {
      var hashes = commitContext.postCommitHashes
      if (hashes == null) {
        hashes = mutableMapOf()
        commitContext.postCommitHashes = hashes
      }

      val head = GitUtil.getHead(repository) ?: return
      val oldHead = hashes.put(repository, head)

      if (oldHead != null) {
        logger<GitPostCommitChangeConverter>().warn("Multiple commits found for $repository: $head - $oldHead")
      }
    }
  }
}