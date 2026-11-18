// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitRemoteBranch
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.filePath
import git4idea.push.GitSpecialRefRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.HostedGitRepositoryRemote
import git4idea.remote.hosting.createHostedGitRepositoryRemote
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

/**
 * Creates the descriptor of the remote for this project.
 * The descriptor uses the host of [defaultCoordinates], so it also matches a remote with an SSH alias.
 */
fun GitLabMergeRequestFullDetails.ProjectDetails.getRemoteDescriptor(defaultCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote =
  createHostedGitRepositoryRemote(path.owner, path.fullPath(), httpUrlToRepo, sshUrlToRepo, defaultCoordinates)

fun GitLabMergeRequestFullDetails.getSourceRemoteDescriptor(defaultCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote? =
  sourceProject?.getRemoteDescriptor(defaultCoordinates)

fun GitLabMergeRequestFullDetails.getTargetRemoteDescriptor(defaultCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote =
  targetProject.getRemoteDescriptor(defaultCoordinates)

/**
 * Gets a special remote ref for the head of the merge request.
 * This special reference does not represent a remote branch,
 * only a reference to the last commit of the MR.
 *
 * https://gitlab.com/gitlab-org/gitlab-foss/-/issues/47110
 */
fun GitLabMergeRequestFullDetails.getSpecialRemoteBranchForHead(remote: GitRemote): GitRemoteBranch =
  GitSpecialRefRemoteBranch("refs/merge-requests/${iid}/head", remote)

fun GitBranchComparisonResult.findLatestCommitWithChangesTo(gitRepository: GitRepository, filePath: FilePath): String? {
  val relativePath = VcsFileUtil.relativePath(gitRepository.root, filePath)
  return commits.lastOrNull { commit -> commit.patches.any { it.filePath == relativePath } }?.sha
}

/**
 * Finds the SHA that the viewed state of [change] uses.
 * The viewed state describes only the cumulative diff, so the result is `null` for a change of a single commit.
 */
internal fun GitBranchComparisonResult.findViewedStateSha(gitRepository: GitRepository, change: RefComparisonChange): String? {
  if (patchesByChange[change]?.isCumulative != true) return null
  return findLatestCommitWithChangesTo(gitRepository, change.filePath)
}
