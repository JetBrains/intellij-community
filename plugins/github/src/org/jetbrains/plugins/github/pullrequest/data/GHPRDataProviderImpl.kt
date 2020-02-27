// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitHistoryUtils
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProviderUtil.backgroundProcessValue
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProviderUtil.joinCancellable
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineMergingModel
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

internal class GHPRDataProviderImpl(private val project: Project,
                                    private val progressManager: ProgressManager,
                                    private val git: Git,
                                    private val securityService: GHPRSecurityService,
                                    private val requestExecutor: GithubApiRequestExecutor,
                                    private val gitRemote: GitRemoteUrlCoordinates,
                                    private val repository: GHRepositoryCoordinates,
                                    override val id: GHPRIdentifier,
                                    override val reviewData: GHPRReviewDataProvider)
  : GHPRDataProvider {

  private val requestsChangesEventDispatcher = EventDispatcher.create(GHPRDataProvider.RequestsChangedListener::class.java)

  private var lastKnownBaseBranch: String? = null
  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  private val detailsRequestValue: LazyCancellableBackgroundProcessValue<GHPullRequest> =
    LazyCancellableBackgroundProcessValue.create(progressManager) {
      val details = requestExecutor.execute(it, GHGQLRequests.PullRequest.findOne(repository, id.number))
                    ?: throw GHNotFoundException("Pull request $id.number does not exist")
      invokeAndWaitIfNeeded {

        var baseBranchChanged = false
        lastKnownBaseBranch?.run { if (this != details.baseRefName) baseBranchChanged = true }
        lastKnownBaseBranch = details.baseRefName
        if (baseBranchChanged) {
          baseBranchProtectionRulesRequestValue.drop()
          reloadMergeabilityState()
        }

        var hashesChanged = false
        lastKnownBaseSha?.run { if (this != details.baseRefOid) hashesChanged = true }
        lastKnownBaseSha = details.baseRefOid
        lastKnownHeadSha?.run { if (this != details.headRefOid) hashesChanged = true }
        lastKnownHeadSha = details.headRefOid
        if (hashesChanged) {
          reloadChanges()
          reloadMergeabilityState()
        }
      }
      details
    }
  override val detailsRequest by backgroundProcessValue(detailsRequestValue)

  private val headBranchFetchRequestValue = LazyCancellableBackgroundProcessValue.create(progressManager) {
    GitFetchSupport.fetchSupport(project)
      .fetch(gitRemote.repository, gitRemote.remote, "refs/pull/${id.number}/head:").throwExceptionIfFailed()
  }
  override val headBranchFetchRequest by backgroundProcessValue(headBranchFetchRequestValue)

  private val baseBranchFetchRequestValue = LazyCancellableBackgroundProcessValue.create(progressManager) {
    val details = detailsRequestValue.value.joinCancellable()
    GitFetchSupport.fetchSupport(project)
      .fetch(gitRemote.repository, gitRemote.remote, details.baseRefName).throwExceptionIfFailed()
    if (!git.getObjectType(gitRemote.repository, details.baseRefOid).outputAsJoinedString.equals("commit", true))
      error("Base revision \"${details.baseRefOid}\" was not fetched from \"${gitRemote.remote.name} ${details.baseRefName}\"")
  }

  private val apiCommitsRequestValue = LazyCancellableBackgroundProcessValue.create(progressManager) { indicator: ProgressIndicator ->
    SimpleGHGQLPagesLoader(requestExecutor, { p ->
      GHGQLRequests.PullRequest.commits(repository, id.number, p)
    }).loadAll(indicator).map { it.commit }
  }
  override val apiCommitsRequest by backgroundProcessValue(apiCommitsRequestValue)

  private val changesProviderValue = LazyCancellableBackgroundProcessValue.create(progressManager) {
    val baseFetch = baseBranchFetchRequestValue.value
    val headFetch = headBranchFetchRequestValue.value

    val detailsRequest = detailsRequestValue.value
    val commitsRequest = apiCommitsRequestValue.value

    val details = detailsRequest.joinCancellable()
    val commits: List<GHCommit> = commitsRequest.joinCancellable()

    val commitsWithDiffs = commits.map { commit ->
      val commitDiff = requestExecutor.execute(it,
                                               GithubApiRequests.Repos.Commits.getDiff(repository, commit.oid))

      val cumulativeDiff = requestExecutor.execute(it,
                                                   GithubApiRequests.Repos.Commits.getDiff(repository, details.baseRefOid, commit.oid))
      Triple(commit, commitDiff, cumulativeDiff)
    }

    //TODO: ??? move to diff and load merge base via API
    baseFetch.joinCancellable()
    headFetch.joinCancellable()

    val mergeBaseRev =
      GitHistoryUtils.getMergeBase(project, gitRemote.repository.root, details.baseRefOid, details.headRefOid)?.rev
      ?: error("Could not calculate merge base for PR branch")

    GHPRChangesProviderImpl(gitRemote.repository, mergeBaseRev, commitsWithDiffs)
  }
  override val changesProviderRequest: CompletableFuture<out GHPRChangesProvider> by backgroundProcessValue(changesProviderValue)

  private val baseBranchProtectionRulesRequestValue = LazyCancellableBackgroundProcessValue.create(progressManager) {
    if (!securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)) return@create null

    val detailsRequest = detailsRequestValue.value
    val baseBranch = detailsRequest.joinCancellable().baseRefName
    try {
      requestExecutor.execute(GithubApiRequests.Repos.Branches.getProtection(repository, baseBranch))
    }
    catch (e: GithubStatusCodeException) {
      if (e.statusCode == 404) null
      else throw e
    }
  }
  private val mergeabilityStateRequestValue = LazyCancellableBackgroundProcessValue.create(progressManager) {
    val detailsRequest = detailsRequestValue.value
    val baseBranchProtectionRulesRequest = baseBranchProtectionRulesRequestValue.value

    val mergeabilityData = requestExecutor.execute(GHGQLRequests.PullRequest.mergeabilityData(repository, id.number))
                           ?: error("Could not find pull request $id.number")
    val builder = GHPRMergeabilityStateBuilder(detailsRequest.joinCancellable(),
                                               mergeabilityData)
    val protectionRules = baseBranchProtectionRulesRequest.joinCancellable()
    if (protectionRules != null) {
      builder.withRestrictions(securityService, protectionRules)
    }
    builder.build()
  }
  override val mergeabilityStateRequest: CompletableFuture<GHPRMergeabilityState>
    by backgroundProcessValue(mergeabilityStateRequestValue)

  private val timelineLoaderHolder = GHPRCountingTimelineLoaderHolder {
    val timelineModel = GHPRTimelineMergingModel()
    GHPRTimelineLoader(progressManager, requestExecutor,
                       repository.serverPath, repository.repositoryPath, id.number,
                       timelineModel)
  }

  override val timelineLoader get() = timelineLoaderHolder.timelineLoader

  override fun acquireTimelineLoader(disposable: Disposable) = timelineLoaderHolder.acquireTimelineLoader(disposable)

  @CalledInAwt
  override fun reloadDetails() {
    detailsRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.detailsRequestChanged()
  }

  @CalledInAwt
  override fun reloadChanges() {
    baseBranchFetchRequestValue.drop()
    headBranchFetchRequestValue.drop()
    changesProviderValue.drop()
    requestsChangesEventDispatcher.multicaster.commitsRequestChanged()
    reviewData.resetReviewThreads()
  }

  override fun reloadMergeabilityState() {
    mergeabilityStateRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.mergeabilityStateRequestChanged()
  }

  override fun addRequestsChangesListener(listener: GHPRDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener)

  override fun addRequestsChangesListener(disposable: Disposable, listener: GHPRDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener, disposable)

  override fun removeRequestsChangesListener(listener: GHPRDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.removeListener(listener)
}