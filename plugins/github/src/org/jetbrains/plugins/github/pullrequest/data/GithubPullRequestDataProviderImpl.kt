// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.history.GitCommitRequirements
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

  private var lastKnownHeadSha: String? = null

  private val detailsRequestValue = backingValue {
    val details = requestExecutor.execute(it, GHGQLRequests.PullRequest.findOne(repository, number))
                  ?: throw GHNotFoundException("Pull request $number does not exist")
    invokeAndWaitIfNeeded {
      lastKnownHeadSha?.run { if (this != details.headRefOid) reloadCommits() }
      lastKnownHeadSha = details.headRefOid
    }
    details
  }
  override val detailsRequest by backgroundProcessValue(detailsRequestValue)

  private val branchFetchRequestValue = backingValue {
    git.fetch(gitRemote.repository, gitRemote.remote, emptyList(), "refs/pull/${number}/head:").throwOnError()
  }
  override val branchFetchRequest by backgroundProcessValue(branchFetchRequestValue)

  private val apiCommitsRequestValue = backingValue {
    GithubApiPagesLoader.loadAll(requestExecutor, it,
                                 GithubApiRequests.Repos.PullRequests.Commits.pages(repository, number))

  }
  override val apiCommitsRequest by backgroundProcessValue(apiCommitsRequestValue)

  private val logCommitsRequestValue = backingValue<List<GitCommit>> {
    branchFetchRequestValue.value.joinCancellable()
    val commitHashes = apiCommitsRequestValue.value.joinCancellable().map { it.sha }
    val gitCommits = mutableListOf<GitCommit>()
    val requirements = GitCommitRequirements(diffRenameLimit = GitCommitRequirements.DiffRenameLimit.INFINITY,
                                             includeRootChanges = false)
    GitLogUtil.readFullDetailsForHashes(project, gitRemote.repository.root, commitHashes, requirements) {
      gitCommits.add(it)
    }

    gitCommits
  }
  override val logCommitsRequest by backgroundProcessValue(logCommitsRequestValue)

  private val diffFileRequestValue = backingValue {
    requestExecutor.execute(it, GithubApiRequests.Repos.PullRequests.getDiff(repository, number))
  }
  private val changesProviderValue = backingValue {
    GHPRChangesProviderImpl(gitRemote.repository,
                            logCommitsRequestValue.value.joinCancellable(),
                            diffFileRequestValue.value.joinCancellable())
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
  override fun reloadCommits() {
    branchFetchRequestValue.drop()
    apiCommitsRequestValue.drop()
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

  private fun <T> backgroundProcessValue(backingValue: LazyCancellableBackgroundProcessValue<T>) =
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