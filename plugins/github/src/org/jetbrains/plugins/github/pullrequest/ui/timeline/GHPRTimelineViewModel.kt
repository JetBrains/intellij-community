// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

interface GHPRTimelineViewModel {
  val repository: GitRepository
  val ghostUser: GHUser
  val currentUser: GHUser

  val canComment: Boolean

  val detailsData: GHPRDetailsDataProvider
  val reviewData: GHPRReviewDataProvider
  val commentsData: GHPRCommentsDataProvider

  val details: StateFlow<ComputedResult<GHPullRequestShort>>

  val timelineLoader: GHListLoader<GHPRTimelineItem>
  val loadingErrorHandler: GHLoadingErrorHandler

  val htmlImageLoader: AsyncHtmlImageLoader
  val avatarIconsProvider: GHAvatarIconsProvider

  fun update()

  fun updateAll()

  companion object {
    val DATA_KEY: DataKey<GHPRTimelineViewModel> = DataKey.create("GitHub.PullRequest.Timeline.ViewModel")
  }
}

internal class GHPRTimelineViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider
) : GHPRTimelineViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline View Model"))

  private val securityService = dataContext.securityService
  private val repositoryDataService = dataContext.repositoryDataService

  override val detailsData = dataProvider.detailsData
  override val reviewData = dataProvider.reviewData
  override val commentsData = dataProvider.commentsData

  override val repository: GitRepository = repositoryDataService.remoteCoordinates.repository

  override val ghostUser: GHUser = securityService.ghostUser
  override val currentUser: GHUser = securityService.currentUser
  override val canComment: Boolean = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)

  override val details: StateFlow<ComputedResult<GHPullRequestShort>> = channelFlow<ComputedResult<GHPullRequestShort>> {
    val disposable = Disposer.newDisposable()
    val fromList = dataContext.listLoader.loadedData.find { it.id == dataProvider.id.id }
    if (fromList != null) {
      trySend(ComputedResult.success(fromList))
    }
    detailsData.loadDetails(disposable) {
      if (!it.isDone) {
        trySend(ComputedResult.loading())
      }
      it.handle { res, err ->
        if (err != null && !CompletableFutureUtil.isCancellation(err)) {
          trySend(ComputedResult.failure(err.cause ?: err))
        }
        else {
          trySend(ComputedResult.success(res))
        }
      }
    }
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  override val timelineLoader = dataProvider.acquireTimelineLoader(cs.nestedDisposable())

  override val loadingErrorHandler: GHLoadingErrorHandler =
    GHApiLoadingErrorHandler(project, securityService.account, timelineLoader::reset)

  override val htmlImageLoader = dataContext.htmlImageLoader
  override val avatarIconsProvider = dataContext.avatarIconsProvider

  override fun update() {
    if (timelineLoader.loadedData.isNotEmpty())
      timelineLoader.loadMore(true)
  }

  override fun updateAll() {
    detailsData.reloadDetails()
    timelineLoader.loadMore(true)
    reviewData.resetReviewThreads()
  }
}