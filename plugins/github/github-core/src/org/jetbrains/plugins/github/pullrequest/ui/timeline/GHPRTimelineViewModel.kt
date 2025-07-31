// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState.PRState
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.UpdateableGHPRTimelineCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.UpdateableGHPRTimelineReviewViewModel
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem as GHPRTimelineItemDTO

interface GHPRTimelineViewModel {
  val ghostUser: GHUser
  val currentUser: GHUser

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

  fun showCommit(oid: String)

  fun openPullRequestInfoAndTimeline(number: Long)

  companion object {
    val DATA_KEY: DataKey<GHPRTimelineViewModel> = DataKey.create("GitHub.PullRequest.Timeline.ViewModel")
  }
}

internal class GHPRTimelineViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRTimelineViewModel {
  private val cs = parentCs.childScope("GitHub Pull Request Timeline View Model", Dispatchers.Main)

  private val vm by lazy { project.service<GHPRProjectViewModel>() }
  private val securityService = dataContext.securityService

  private val detailsData = dataProvider.detailsData
  private val reviewData = dataProvider.reviewData
  private val commentsData = dataProvider.commentsData

  private val interactionState = dataContext.interactionState

  override val ghostUser: GHUser = securityService.ghostUser
  override val currentUser: GHUser = securityService.currentUser

  override val detailsVm = GHPRDetailsTimelineViewModel(project, parentCs, dataContext, dataProvider)
  private val timelineLoader = dataProvider.acquireTimelineLoader(cs)

  override val loadingErrorHandler: GHLoadingErrorHandler =
    GHApiLoadingErrorHandler(project, securityService.account, GHLoginSource.PR_TIMELINE, timelineLoader::reset)

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

      // Update the last seen date to the last time a fully loaded timeline was loaded
      if (!timelineLoader.canLoadMore() && !timelineLoader.loading) {
        updateLastSeen(System.currentTimeMillis())
      }
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

  val showCommitRequests = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val showDiffRequests = MutableSharedFlow<ChangesSelection>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val timelineModel = GHPRTimelineMergingModel()
    timelineModel.add(timelineLoader.loadedData)
    val itemsFromModel = MutableStateFlow(timelineModel.getItemsList())
    timelineItems = itemsFromModel.mapDataToModel(
      ::getItemID,
      { createItemFromDTO(it) },
      { update(it) }
    ).stateIn(cs, SharingStarted.Eagerly, emptyList())

    timelineLoader.addDataListener(cs.nestedDisposable(), object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = timelineLoader.loadedData
        val addedData = loadedData.subList(startIdx, loadedData.size)
        timelineModel.add(addedData)
        itemsFromModel.value = timelineModel.getItemsList()

        val latestLoadedItemTime = loadedData.mapNotNull { it.createdAt }.maxOrNull()?.time
        updateLastSeen(latestLoadedItemTime ?: System.currentTimeMillis())
      }

      override fun onDataUpdated(idx: Int) {
        val newItem = timelineLoader.loadedData[idx]
        timelineModel.update(idx, newItem)
        itemsFromModel.value = timelineModel.getItemsList()

        updateLastSeen(System.currentTimeMillis())
      }

      override fun onDataRemoved(idx: Int) {
        timelineModel.remove(idx)
        itemsFromModel.value = timelineModel.getItemsList()

        updateLastSeen(System.currentTimeMillis())
      }

      override fun onAllDataRemoved() {
        timelineModel.removeAll()
        itemsFromModel.value = timelineModel.getItemsList()
        timelineLoader.loadMore()
      }
    })
  }

  private fun updateLastSeen(lastSeenMillis: Long) {
    val prId = detailsVm.details.value.getOrNull()?.id ?: return
    interactionState.updateStateFor(prId) { st ->
      PRState(prId, maxOf(lastSeenMillis, st?.lastSeen ?: 0L))
    }
  }

  private fun getItemID(data: GHPRTimelineItemDTO): Any =
    when (data) {
      is GHNode -> data.id
      else -> data
    }

  private fun CoroutineScope.createItemFromDTO(data: GHPRTimelineItemDTO): GHPRTimelineItem =
    when (data) {
      is GHIssueComment -> {
        UpdateableGHPRTimelineCommentViewModel(project, this, dataContext, dataProvider.commentsData, data)
      }
      is GHPullRequestReview -> {
        UpdateableGHPRTimelineReviewViewModel(project, this, dataContext, dataProvider, data).also {
          launchNow {
            it.showDiffRequests.collect(showDiffRequests)
          }
        }
      }
      is GHPullRequestCommitShort -> GHPRTimelineItem.Commits(listOf(data))
      is GHPRTimelineGroupedCommits -> GHPRTimelineItem.Commits(data.items)
      is GHPRTimelineEvent -> GHPRTimelineItem.Event(data)
      else -> GHPRTimelineItem.Unknown("")
    }

  private fun GHPRTimelineItem.update(data: GHPRTimelineItemDTO) {
    if (this is UpdateableGHPRTimelineCommentViewModel && data is GHIssueComment) {
      update(data)
    }
  }

  override fun requestMore() {
    timelineLoader.loadMore()
  }

  override fun update() {
    if (timelineLoader.loadedData.isNotEmpty())
      timelineLoader.loadMore(true)
  }

  override fun updateAll() {
    cs.launch {
      detailsData.signalDetailsNeedReload()
      detailsData.signalMergeabilityNeedsReload()
      timelineLoader.loadMore(true)
      reviewData.signalThreadsNeedReload()
    }
  }

  override fun showCommit(oid: String) {
    showCommitRequests.tryEmit(oid)
  }

  override fun openPullRequestInfoAndTimeline(number: Long) {
    vm.connectedProjectVm.value?.openPullRequestInfoAndTimeline(number)
  }
}

private fun GHPRTimelineMergingModel.getItemsList(): List<GHPRTimelineItemDTO> =
  buildList {
    for (i in 0 until getSize()) {
      add(getElementAt(i))
    }
  }