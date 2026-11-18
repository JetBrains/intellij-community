// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.map
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitStandardRemoteBranch
import git4idea.changes.createVcsChange
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitCodeReviewUtils
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.remote.hosting.localCommitsSyncStatus
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestBranchUtil

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

  override fun hasIncomingCommits(commits: List<String>): Flow<ComputedResult<Boolean>?> =
    flowOf(commits).localCommitsSyncStatus(repository).map { result -> result?.map { it?.incoming == true } }

  override suspend fun checkoutBranch(details: GitLabMergeRequestFullDetails) {
    GitLabMergeRequestBranchUtil.fetchAndCheckoutBranch(remote, details)
  }

  override suspend fun findFilePath(file: VirtualFile): FilePath? {
    if (!file.isValid || file.isDirectory || !VfsUtilCore.isAncestor(repository.root, file, true)) return null
    return VcsContextFactory.getInstance().createFilePathOn(file)
  }

  override suspend fun loadContentAfter(change: RefComparisonChange): String = withContext(Dispatchers.IO) {
    val content = coroutineToIndicator {
      change.createVcsChange(repository.project).afterRevision?.content ?: ""
    }
    StringUtil.convertLineSeparators(content)
  }

  override suspend fun isLinkedWorktree(): Boolean =
    GitWorkingTreesService.getInstance(repository.project).isCurrentProjectLinkedWorktree()

  override suspend fun deleteWorktree() {
    GitWorkingTreesService.getInstance(repository.project).deleteCurrentProjectWorktree()
  }
}
