// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.service
import com.intellij.vcs.git.shared.ref.GitCurrentRef
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.ref.GitReferencesSet
import com.intellij.vcs.git.shared.repo.GitHash
import com.intellij.vcs.git.shared.repo.GitOperationState
import com.intellij.vcs.git.shared.repo.GitRepositoryState
import com.intellij.vcs.git.shared.rpc.GitRepositoryDto
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitTagType
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager

internal object GitRepositoryToDtoConverter {
  fun convertToDto(repository: GitRepository): GitRepositoryDto {
    return GitRepositoryDto(
      repositoryId = repository.rpcId,
      shortName = VcsUtil.getShortVcsRootName(repository.project, repository.root),
      state = convertRepositoryState(repository),
      favoriteRefs = collectFavorites(repository)
    )
  }

  fun convertRepositoryState(repository: GitRepository): GitRepositoryState {
    val refsSet = GitReferencesSet(
      repository.info.localBranchesWithHashes.keys,
      repository.info.remoteBranchesWithHashes.keys.filterIsInstance<GitStandardRemoteBranch>().toSet(),
      repository.tagHolder.getTags().keys,
    )
    return GitRepositoryState(
      currentRef = GitCurrentRef.wrap(GitRefUtil.getCurrentReference(repository)),
      revision = repository.currentRevision?.let { GitHash(it) },
      refs = refsSet,
      recentBranches = repository.branches.recentCheckoutBranches,
      operationState = convertOperationState(repository),
      trackingInfo = convertTrackingInfo(repository.info.branchTrackInfosMap)
    )
  }

  fun collectFavorites(repository: GitRepository): GitFavoriteRefs {
    val branchManager = repository.project.service<GitBranchManager>()
    return GitFavoriteRefs(
      localBranches = branchManager.getFavoriteRefs(GitBranchType.LOCAL, repository),
      remoteBranches = branchManager.getFavoriteRefs(GitBranchType.REMOTE, repository),
      tags = branchManager.getFavoriteRefs(GitTagType, repository),
    )
  }

  private fun convertOperationState(repository: GitRepository): GitOperationState = when (repository.state) {
    Repository.State.NORMAL -> GitOperationState.NORMAL
    Repository.State.MERGING -> GitOperationState.MERGE
    Repository.State.REBASING -> GitOperationState.REBASE
    Repository.State.GRAFTING -> GitOperationState.CHERRY_PICK
    Repository.State.REVERTING -> GitOperationState.REVERT
    Repository.State.DETACHED -> GitOperationState.DETACHED_HEAD
  }

  private fun convertTrackingInfo(trackingInfo: Map<String, GitBranchTrackInfo>): Map<String, GitStandardRemoteBranch> {
    val result = HashMap<String, GitStandardRemoteBranch>(trackingInfo.size)

    trackingInfo.forEach { (branchName, trackInfo) ->
      val remoteBranch = trackInfo.remoteBranch
      if (remoteBranch is GitStandardRemoteBranch) {
        result[branchName] = remoteBranch
      }
    }

    return result
  }
}