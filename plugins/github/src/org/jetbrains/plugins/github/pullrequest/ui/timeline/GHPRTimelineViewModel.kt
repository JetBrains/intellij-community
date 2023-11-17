// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

interface GHPRTimelineViewModel {
  val securityService: GHPRSecurityService
  val repositoryDataService: GHPRRepositoryDataService
  val htmlImageLoader: AsyncHtmlImageLoader
  val avatarIconsProvider: GHAvatarIconsProvider
  val detailsData: GHPRDetailsDataProvider
  val reviewData: GHPRReviewDataProvider
  val commentsData: GHPRCommentsDataProvider
  val timelineLoader: GHListLoader<GHPRTimelineItem>

  val details: StateFlow<ComputedResult<GHPullRequestShort>>

  fun update()
}

internal class GHPRTimelineViewModelImpl(
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider
) : GHPRTimelineViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline View Model"))

  override val securityService = dataContext.securityService
  override val repositoryDataService = dataContext.repositoryDataService
  override val htmlImageLoader = dataContext.htmlImageLoader
  override val avatarIconsProvider = dataContext.avatarIconsProvider

  override val detailsData = dataProvider.detailsData
  override val reviewData = dataProvider.reviewData
  override val commentsData = dataProvider.commentsData

  override val timelineLoader = dataProvider.acquireTimelineLoader(cs.nestedDisposable())

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

  override fun update() {
    if (timelineLoader.loadedData.isNotEmpty())
      timelineLoader.loadMore(true)
  }
}