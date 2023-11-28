// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
import org.jetbrains.plugins.github.ui.cloneDialog.GHCloneDialogExtensionComponentBase.Companion.items

interface GHPRTimelineViewModel {
  val prId: GHPRIdentifier
  val repository: GitRepository
  val ghostUser: GHUser
  val currentUser: GHUser

  val detailsData: GHPRDetailsDataProvider
  val reviewData: GHPRReviewDataProvider
  val commentsData: GHPRCommentsDataProvider

  val detailsVm: GHPRDetailsTimelineViewModel

  val timelineItems: StateFlow<List<GHPRTimelineItem>>
  val isLoading: StateFlow<Boolean>
  val loadingError: StateFlow<Throwable?>

  val loadingErrorHandler: GHLoadingErrorHandler

  val commentVm: GHPRNewCommentViewModel?

  val htmlImageLoader: AsyncHtmlImageLoader
  val avatarIconsProvider: GHAvatarIconsProvider

  fun update()

  fun updateAll()

  fun requestMore()

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
  private val cs = parentCs.childScope(Dispatchers.Main + CoroutineName("GitHub Pull Request Timeline View Model"))

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
  private val timelineLoader = dataProvider.acquireTimelineLoader(cs.nestedDisposable())

  override val loadingErrorHandler: GHLoadingErrorHandler =
    GHApiLoadingErrorHandler(project, securityService.account, timelineLoader::reset)

  override val commentVm: GHPRNewCommentViewModel? =
    if (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
      GHPRNewCommentViewModel(project, parentCs, commentsData)
    }
    else null

  override val htmlImageLoader = dataContext.htmlImageLoader
  override val avatarIconsProvider = dataContext.avatarIconsProvider

  override val timelineItems: StateFlow<List<GHPRTimelineItem>>

  override val isLoading: StateFlow<Boolean> = callbackFlow {
    val disposable = Disposer.newDisposable()
    timelineLoader.addLoadingStateChangeListener(disposable) {
      trySend(timelineLoader.loading)
    }
    send(timelineLoader.loading)
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Eagerly, timelineLoader.loading)

  override val loadingError: StateFlow<Throwable?> = callbackFlow {
    val disposable = Disposer.newDisposable()
    timelineLoader.addErrorChangeListener(disposable) {
      trySend(timelineLoader.error)
    }
    send(timelineLoader.error)
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Eagerly, timelineLoader.error)


  init {
    val timelineModel = GHPRTimelineMergingModel()
    timelineModel.add(timelineLoader.loadedData)
    timelineItems = MutableStateFlow(timelineModel.items.toList())

    timelineLoader.addDataListener(cs.nestedDisposable(), object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = timelineLoader.loadedData
        timelineModel.add(loadedData.subList(startIdx, loadedData.size))
        timelineItems.value = timelineModel.getItemsList()
      }

      override fun onDataUpdated(idx: Int) {
        val newItem = timelineLoader.loadedData[idx]
        timelineModel.update(idx, newItem)
        timelineItems.value = timelineModel.getItemsList()
      }

      override fun onDataRemoved(idx: Int) {
        timelineModel.remove(idx)
        timelineItems.value = timelineModel.getItemsList()
      }

      override fun onAllDataRemoved() {
        timelineModel.removeAll()
        timelineItems.value = timelineModel.getItemsList()
        timelineLoader.loadMore()
      }
    })
  }

  override fun requestMore() {
    timelineLoader.loadMore()
  }

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

private fun GHPRTimelineMergingModel.getItemsList(): List<GHPRTimelineItem> =
  buildList {
    for (i in 0 until getSize()) {
      add(getElementAt(i))
    }
  }