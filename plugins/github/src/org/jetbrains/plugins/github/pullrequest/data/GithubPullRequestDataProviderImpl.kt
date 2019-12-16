// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.EventDispatcher
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitLogUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineMergingModel
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class GithubPullRequestDataProviderImpl(private val project: Project,
                                                 private val progressManager: ProgressManager,
                                                 private val git: Git,
                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                 private val gitRemote: GitRemoteUrlCoordinates,
                                                 private val repository: GHRepositoryCoordinates,
                                                 override val number: Long)
  : GithubPullRequestDataProvider {

  private val requestsChangesEventDispatcher = EventDispatcher.create(GithubPullRequestDataProvider.RequestsChangedListener::class.java)

  private var lastKnownBaseSha: String? = null
  private var lastKnownHeadSha: String? = null

  private val detailsRequestValue = backingValue {
    val details = requestExecutor.execute(it, GHGQLRequests.PullRequest.findOne(repository, number))
                  ?: throw GHNotFoundException("Pull request $number does not exist")
    invokeAndWaitIfNeeded {
      var needReload = false
      lastKnownBaseSha?.run { if (this != details.baseRefOid) needReload = true }
      lastKnownBaseSha = details.baseRefOid
      lastKnownHeadSha?.run { if (this != details.headRefOid) needReload = true }
      lastKnownHeadSha = details.headRefOid
      if (needReload) reloadChanges()
    }
    details
  }
  override val detailsRequest by backgroundProcessValue(detailsRequestValue)

  private val headBranchFetchRequestValue = backingValue {
    GitFetchSupport.fetchSupport(project)
      .fetch(gitRemote.repository, gitRemote.remote, "refs/pull/${number}/head:").throwExceptionIfFailed()
  }
  override val headBranchFetchRequest by backgroundProcessValue(headBranchFetchRequestValue)

  private val baseBranchFetchRequestValue = backingValue {
    val details = detailsRequestValue.value.joinCancellable()
    GitFetchSupport.fetchSupport(project)
      .fetch(gitRemote.repository, gitRemote.remote, details.baseRefName).throwExceptionIfFailed()
    if (!git.getObjectType(gitRemote.repository, details.baseRefOid).outputAsJoinedString.equals("commit", true))
      error("Base revision \"${details.baseRefOid}\" was not fetched from \"${gitRemote.remote.name} ${details.baseRefName}\"")
  }

  private val apiCommitsRequestValue = backingValue {
    GithubApiPagesLoader.loadAll(requestExecutor, it,
                                 GithubApiRequests.Repos.PullRequests.Commits.pages(repository, number))

  }
  override val apiCommitsRequest by backgroundProcessValue(apiCommitsRequestValue)

  private val logCommitsRequestValue = backingValue<List<GitCommit>> {
    val baseFetch = baseBranchFetchRequestValue.value
    val headFetch = headBranchFetchRequestValue.value
    val detailsRequest = detailsRequestValue.value

    baseFetch.joinCancellable()
    headFetch.joinCancellable()
    val details = detailsRequest.joinCancellable()

    val gitCommits = mutableListOf<GitCommit>()
    GitLogUtil.readFullDetails(project, gitRemote.repository.root, Consumer {
      gitCommits.add(it)
    }, "${details.baseRefOid}..${details.headRefOid}")

    gitCommits.asReversed()
  }
  override val logCommitsRequest by backgroundProcessValue(logCommitsRequestValue)

  private val diffFileRequestValue = backingValue {
    requestExecutor.execute(it, GithubApiRequests.Repos.PullRequests.getDiff(repository, number))
  }
  private val changesProviderValue = backingValue {
    val detailsRequest = detailsRequestValue.value
    val commitsRequest = logCommitsRequestValue.value
    val diffFileRequest = diffFileRequestValue.value

    val details = detailsRequest.joinCancellable()
    val commits = commitsRequest.joinCancellable()
    val diffFile = diffFileRequest.joinCancellable()
    GHPRChangesProviderImpl(gitRemote.repository,
                            details.baseRefOid, details.headRefOid,
                            commits, diffFile)
  }
  override val changesProviderRequest: CompletableFuture<out GHPRChangesProvider> by backgroundProcessValue(changesProviderValue)

  private val reviewThreadsRequestValue = backingValue {
    SimpleGHGQLPagesLoader(requestExecutor, { p ->
      GHGQLRequests.PullRequest.reviewThreads(repository, number, p)
    }).loadAll(it)
  }
  override val reviewThreadsRequest: CompletableFuture<List<GHPullRequestReviewThread>> by backgroundProcessValue(reviewThreadsRequestValue)

  private val timelineLoaderHolder = GHPRCountingTimelineLoaderHolder {
    val timelineModel = GHPRTimelineMergingModel()
    GHPRTimelineLoader(progressManager, requestExecutor,
                       repository.serverPath, repository.repositoryPath, number,
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
    logCommitsRequestValue.drop()
    changesProviderValue.drop()
    requestsChangesEventDispatcher.multicaster.commitsRequestChanged()
    reloadReviewThreads()
  }

  @CalledInAwt
  override fun reloadReviewThreads() {
    reviewThreadsRequestValue.drop()
    requestsChangesEventDispatcher.multicaster.reviewThreadsRequestChanged()
  }

  @Throws(ProcessCanceledException::class)
  private fun <T> CompletableFuture<T>.joinCancellable(): T {
    try {
      return join()
    }
    catch (e: CancellationException) {
      throw ProcessCanceledException(e)
    }
    catch (e: CompletionException) {
      if (GithubAsyncUtil.isCancellation(e)) throw ProcessCanceledException(e)
      throw GithubAsyncUtil.extractError(e)
    }
  }

  private fun <T> backingValue(supplier: (ProgressIndicator) -> T) =
    object : LazyCancellableBackgroundProcessValue<T>(progressManager) {
      override fun compute(indicator: ProgressIndicator) = supplier(indicator)
    }

  private fun <T> backgroundProcessValue(backingValue: LazyCancellableBackgroundProcessValue<T>): ReadOnlyProperty<Any?, CompletableFuture<T>> =
    object : ReadOnlyProperty<Any?, CompletableFuture<T>> {
      override fun getValue(thisRef: Any?, property: KProperty<*>) =
        GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { backingValue.value } }
    }

  override fun addRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener)

  override fun addRequestsChangesListener(disposable: Disposable, listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.addListener(listener, disposable)

  override fun removeRequestsChangesListener(listener: GithubPullRequestDataProvider.RequestsChangedListener) =
    requestsChangesEventDispatcher.removeListener(listener)
}