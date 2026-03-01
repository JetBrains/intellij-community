// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.vcs.git.ref.GitCurrentRef
import com.intellij.vcs.git.ref.GitFavoriteRefs
import com.intellij.vcs.git.repo.GitHash
import com.intellij.vcs.git.repo.GitOperationState
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.repo.GitRepositoryState
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import git4idea.GitWorkingTree
import git4idea.remoteApi.GitRepositoryToDtoConverter.convertTrackingInfo
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

internal class MockGitRepositoryModel(repo: GitRepository) : GitRepositoryModel {
  override val repositoryId: RepositoryId = repo.repositoryId()
  override val shortName: String = VcsUtil.getShortVcsRootName(repo.project, repo.root)
  override val state: GitRepositoryState = MockGitRepositoryState(repo)
  override val favoriteRefs: GitFavoriteRefs
    get() = throw UnsupportedOperationException()
  override val root = VcsUtil.getFilePath(repo.root)

  private class MockGitRepositoryState(repo: GitRepository) : GitRepositoryState {
    override val currentRef: GitCurrentRef? = GitCurrentRef.wrap(GitRefUtil.getCurrentReference(repo))
    override val revision: @NlsSafe GitHash? = repo.currentRevision?.let { GitHash(it) }
    override val localBranches: Set<GitStandardLocalBranch> = repo.info.localBranchesWithHashes.keys
    override val remoteBranches: Set<GitStandardRemoteBranch> = repo.info.remoteBranchesWithHashes.keys.filterIsInstance<GitStandardRemoteBranch>().toSet()
    override val tags: Set<GitTag>
      get() = throw UnsupportedOperationException()
    override val recentBranches: List<GitStandardLocalBranch> = repo.branches.recentCheckoutBranches
    override val operationState: GitOperationState
      get() = throw UnsupportedOperationException()
    private val trackingInfo: Map<String, GitStandardRemoteBranch> = convertTrackingInfo(repo.info.branchTrackInfosMap)
    override val workingTrees: Collection<GitWorkingTree> = repo.workingTreeHolder.getWorkingTrees()

    override fun getDisplayableBranchText(): @Nls String =
      throw UnsupportedOperationException()

    override fun getTrackingInfo(branch: GitStandardLocalBranch): GitStandardRemoteBranch? =
      trackingInfo[branch.name]
  }
}