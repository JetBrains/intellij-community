// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

class GHPRRepositoryDataServiceImpl internal constructor(progressManager: ProgressManager,
                                                         private val requestExecutor: GithubApiRequestExecutor,
                                                         private val serverPath: GithubServerPath,
                                                         private val repoPath: GHRepositoryPath,
                                                         private val repoOwner: GHRepositoryOwnerName)
  : GHPRRepositoryDataService {

  init {
    requestExecutor.addListener(this) {
      resetData()
    }
  }

  private val collaboratorsValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Collaborators.pages(serverPath, repoPath.owner, repoPath.repository))
      .filter { it.permissions.isPush }
      .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
  }

  override val collaboratorsWithPushAccess: CompletableFuture<List<GHUser>>
    get() = collaboratorsValue.value

  private val teamsValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    if (repoOwner !is GHRepositoryOwnerName.Organization) emptyList()
    else SimpleGHGQLPagesLoader(requestExecutor, {
      GHGQLRequests.Organization.Team.findAll(serverPath, repoOwner.login, it)
    }).loadAll(indicator)
  }

  override val teams: CompletableFuture<List<GHTeam>>
    get() = teamsValue.value

  override val potentialReviewers: CompletableFuture<List<GHPullRequestRequestedReviewer>>
    get() = collaboratorsWithPushAccess.thenCombine(teams,
                                                    BiFunction<List<GHUser>, List<GHTeam>, List<GHPullRequestRequestedReviewer>> { users, teams ->
                                                      users + teams
                                                    })

  private val assigneesValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Assignees.pages(serverPath, repoPath.owner, repoPath.repository))
      .map { GHUser(it.nodeId, it.login, it.htmlUrl, it.avatarUrl ?: "", null) }
  }

  override val issuesAssignees: CompletableFuture<List<GHUser>>
    get() = assigneesValue.value

  private val labelsValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator ->
    GithubApiPagesLoader
      .loadAll(requestExecutor, indicator,
               GithubApiRequests.Repos.Labels.pages(serverPath, repoPath.owner, repoPath.repository))
      .map { GHLabel(it.nodeId, it.url, it.name, it.color) }
  }

  override val labels: CompletableFuture<List<GHLabel>>
    get() = labelsValue.value

  override fun resetData() {
    collaboratorsValue.drop()
    teamsValue.drop()
    assigneesValue.drop()
    labelsValue.drop()
  }

  override fun dispose() {
    resetData()
  }
}