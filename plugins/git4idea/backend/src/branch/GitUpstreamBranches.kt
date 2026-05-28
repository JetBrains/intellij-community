// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import git4idea.GitRemoteBranch
import git4idea.commands.Git
import git4idea.repo.GitRepository

internal class GitUpstreamBranches(
  repositories: Collection<GitRepository>,
  private val localBranchName: String,
  private val git: Git,
) {
  private val upstreamBranches: Map<GitRepository, GitRemoteBranch> = findUpstreamBranches(repositories, localBranchName)

  fun get(): Map<GitRepository, GitRemoteBranch> = upstreamBranches

  fun restoreUpstream(repository: GitRepository) {
    val upstreamBranch = upstreamBranches[repository] ?: return
    val result = git.setUpstream(repository, upstreamBranch.nameForLocalOperations, localBranchName)
    if (!result.success()) {
      GitBranchOperation.LOG.warn(
        "Couldn't set $localBranchName to track $upstreamBranch in ${repository.root.name}: ${result.errorOutputAsJoinedString}"
      )
    }
  }

  private fun findUpstreamBranches(
    repositories: Collection<GitRepository>,
    localBranchName: String,
  ): Map<GitRepository, GitRemoteBranch> {
    return repositories.mapNotNull { repository ->
      repository.getBranchTrackInfo(localBranchName)?.let { trackInfo ->
        repository to trackInfo.remoteBranch
      }
    }.toMap()
  }
}