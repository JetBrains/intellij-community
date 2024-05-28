// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.util.text.nullize
import git4idea.GitRemoteBranch
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
}