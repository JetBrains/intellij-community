// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitStandardRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitCodeReviewUtils
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.flow.Flow

/**
 * The git repository of [remote].
 *
 * The changes use [GitRevisionNumber], so that the code which loads their content can use them.
 */
internal class GitLabGitLocalRepository(private val remote: GitRemoteUrlCoordinates) : GitLabLocalRepository {
  private val repository = remote.repository

  override val changes: Flow<Unit> = repository.changesSignalFlow()

  override fun filePath(relativePath: String): FilePath = VcsUtil.getFilePath(repository.root, relativePath)

  override fun revisionNumber(sha: String): ShortVcsRevisionNumber = GitRevisionNumber(sha)

  override fun isInSync(details: GitLabMergeRequestFullDetails): Boolean? {
    val remoteMrBranchHash = details.getSourceRemoteDescriptor(remote)?.let {
      GitRemoteBranchesUtil.findRemote(repository, it)
    }?.let {
      val branch = GitStandardRemoteBranch(it, details.sourceBranch)
      repository.branches.getHash(branch)
    } ?: return null
    val knownHead = details.diffRefs?.headSha ?: return null
    return remoteMrBranchHash.asString() == knownHead
  }

  override suspend fun ensureRevisionsFetched(details: GitLabMergeRequestFullDetails, revisions: List<String>) {
    if (GitCodeReviewUtils.testRevisionsExist(repository, revisions)) return

    GitCodeReviewUtils.fetch(repository, remote.remote, details.targetBranch)
    GitCodeReviewUtils.fetch(repository, remote.remote, """merge-requests/${details.iid}/head:""")

    check(GitCodeReviewUtils.testRevisionsExist(repository, revisions)) {
      "Failed to fetch some revisions"
    }
  }

  override suspend fun loadPatches(baseRev: String, headRev: String, paths: List<String>): List<TextFilePatch> =
    GitCodeReviewUtils.loadPatches(repository, baseRev, headRev, paths)
}
