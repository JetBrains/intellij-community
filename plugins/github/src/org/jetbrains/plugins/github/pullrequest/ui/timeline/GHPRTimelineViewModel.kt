// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

interface GHPRTimelineViewModel {
  val prId: GHPRIdentifier
  val repository: GitRepository
  val ghostUser: GHUser
  val currentUser: GHUser

  val detailsData: GHPRDetailsDataProvider
  val reviewData: GHPRReviewDataProvider
  val commentsData: GHPRCommentsDataProvider

  val detailsVm: GHPRDetailsTimelineViewModel

  val timelineLoader: GHListLoader<GHPRTimelineItem>
  val loadingErrorHandler: GHLoadingErrorHandler

  val commentVm: GHPRNewCommentViewModel?

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

  override val prId: GHPRIdentifier = dataProvider.id
  override val detailsData = dataProvider.detailsData
  override val reviewData = dataProvider.reviewData
  override val commentsData = dataProvider.commentsData

  override val repository: GitRepository = repositoryDataService.remoteCoordinates.repository

  override val ghostUser: GHUser = securityService.ghostUser
  override val currentUser: GHUser = securityService.currentUser

  override val detailsVm = GHPRDetailsTimelineViewModel(project, parentCs, dataContext, dataProvider)
  override val timelineLoader = dataProvider.acquireTimelineLoader(cs.nestedDisposable())

  override val loadingErrorHandler: GHLoadingErrorHandler =
    GHApiLoadingErrorHandler(project, securityService.account, timelineLoader::reset)

  override val commentVm: GHPRNewCommentViewModel? =
    if (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
      GHPRNewCommentViewModel(project, parentCs, commentsData)
    }
    else null

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