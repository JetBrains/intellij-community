// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import com.intellij.util.asSafely
import com.intellij.util.text.nullize
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.GitRevisionNumber
import git4idea.changes.GitChangeUtils
import git4idea.history.GitCommitRequirements
import git4idea.history.GitHistoryUtils
import git4idea.history.GitLogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.toPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

internal class GHPRCreationServiceImpl(private val requestExecutor: GithubApiRequestExecutor,
                                       repositoryDataService: GHPRRepositoryDataService) : GHPRCreationService {

  private val baseRepo = repositoryDataService.repositoryMapping
  private val repositoryId = repositoryDataService.repositoryId

  override suspend fun createPullRequest(baseBranch: GitRemoteBranch,
                                         headRepo: GHGitRepositoryMapping,
                                         headBranch: GitRemoteBranch,
                                         title: String,
                                         description: String,
                                         draft: Boolean): GHPullRequestShort {
    val headRepositoryPrefix = getHeadRepoPrefix(headRepo)

    val actualTitle = title.takeIf(String::isNotBlank) ?: headBranch.nameForRemoteOperations
    val body = description.nullize(true)

    return requestExecutor.executeSuspend(GHGQLRequests.PullRequest.create(baseRepo.repository, repositoryId,
                                                                           baseBranch.nameForRemoteOperations,
                                                                           headRepositoryPrefix + headBranch.nameForRemoteOperations,
                                                                           actualTitle, body, draft
    ))
  }

  override suspend fun findOpenPullRequest(baseBranch: GitRemoteBranch?,
                                           headRepo: GHRepositoryPath,
                                           headBranch: GitRemoteBranch): GHPRIdentifier? =
    requestExecutor.executeSuspend(
      GithubApiRequests.Repos.PullRequests.find(baseRepo.repository,
                                                GithubIssueState.open,
                                                baseBranch?.nameForRemoteOperations,
                                                headRepo.owner + ":" + headBranch.nameForRemoteOperations
      )).items.firstOrNull()?.toPRIdentifier()

  private fun getHeadRepoPrefix(headRepo: GHGitRepositoryMapping) =
    if (baseRepo.repository == headRepo.repository) "" else headRepo.repository.repositoryPath.owner + ":"

  override suspend fun getDiff(commit: VcsCommitMetadata): Collection<RefComparisonChange> {
    return withContext(Dispatchers.IO) {
      coroutineToIndicator {
        buildList {
          GitLogUtil.readFullDetailsForHashes(baseRepo.gitRepository.project, baseRepo.gitRepository.root,
                                              listOf(commit.id.asString()),
                                              GitCommitRequirements.DEFAULT) { gitCommit ->
            gitCommit.changes.forEach { change ->
              add(change.toComparisonChange(commit.parents.first().asString(), commit.id.asString()))
            }
          }
        }
      }
    }
  }

  override suspend fun getDiff(baseBranch: GitRemoteBranch, headBranch: GitBranch): Collection<RefComparisonChange> {
    return withContext(Dispatchers.IO) {
      coroutineToIndicator {
        val mergeBase = GitHistoryUtils.getMergeBase(baseRepo.gitRepository.project, baseRepo.gitRepository.root,
                                                     baseBranch.name, headBranch.name)
                        ?: error("Unrelated branches ${baseBranch.name} ${headBranch.name}")
        val headRef = baseRepo.gitRepository.branches.getHash(headBranch) ?: error("Branch ${headBranch.name} has bo revision")
        GitChangeUtils.getThreeDotDiffOrThrow(baseRepo.gitRepository, baseBranch.name, headBranch.name).map {
          it.toComparisonChange(mergeBase.rev, headRef.asString())
        }
      }
    }
  }

  private fun Change.toComparisonChange(commitBefore: String, commitAfter: String): RefComparisonChange {
    val beforeRef = beforeRevision?.revisionNumber?.asSafely<ShortVcsRevisionNumber>() ?: GitRevisionNumber(commitBefore)
    val afterRef = afterRevision?.revisionNumber?.asSafely<ShortVcsRevisionNumber>() ?: GitRevisionNumber(commitAfter)
    return RefComparisonChange(
      beforeRef, beforeRevision?.file, afterRef, afterRevision?.file
    )
  }
}