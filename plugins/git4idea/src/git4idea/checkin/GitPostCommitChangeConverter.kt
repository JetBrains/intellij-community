// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.PostCommitChangeConverter
import com.intellij.vcs.commit.commitProperty
import com.intellij.vcs.log.Hash
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitPostCommitChangeConverter(private val project: Project) : PostCommitChangeConverter {
  override fun convertChangesAfterCommit(changes: List<Change>, commitContext: CommitContext): List<Change> {
    val hashes = commitContext.postCommitHashes ?: return changes
    return changes.map { convertChangeAfterCommit(it, hashes) ?: it }
  }

  private fun convertChangeAfterCommit(change: Change, hashes: Map<GitRepository, Hash>): Change? {
    val filePath = ChangesUtil.getFilePath(change)
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(filePath) ?: return null
    val commitHash = hashes[repository] ?: return null

    val bRev = change.beforeRevision
    val fixedBRev = when {
      bRev != null -> GitContentRevision.createRevision(bRev.file, GitRevisionNumber("${commitHash.asString()}~1"), project)
      else -> null
    }

    val aRev = change.afterRevision
    val fixedARev = when {
      aRev != null -> GitContentRevision.createRevision(aRev.file, GitRevisionNumber(commitHash.asString()), project)
      else -> null
    }

    return Change(fixedBRev, fixedARev, change.fileStatus)
  }

  companion object {
    private val GIT_POST_COMMIT_HASHES_KEY = Key.create<MutableMap<GitRepository, Hash>>("Git.Post.Commit.Hash")

    private var CommitContext.postCommitHashes: MutableMap<GitRepository, Hash>? by commitProperty(GIT_POST_COMMIT_HASHES_KEY, null)

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