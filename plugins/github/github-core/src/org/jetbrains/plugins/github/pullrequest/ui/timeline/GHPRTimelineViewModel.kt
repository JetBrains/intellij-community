// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.AsyncIncrementalListComputer
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState.PRState
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class GHPRTimelineViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val viewModelWithTextCompletion: GHViewModelWithTextCompletion,
) : GHPRTimelineViewModel {
  private val cs = parentCs.childScope("GitHub Pull Request Timeline View Model", Dispatchers.Default)

  private val vm by lazy { project.service<GHPRProjectViewModel>() }
  private val securityService = dataContext.securityService

  private val detailsData = dataProvider.detailsData
  private val timelineData = dataProvider.timelineData
  private val reviewData = dataProvider.reviewData
  private val commentsData = dataProvider.commentsData

  private val interactionState = dataContext.interactionState

  override val ghostUser: GHUser = securityService.ghostUser
  override val currentUser: GHUser = securityService.currentUser

  override val detailsVm = GHPRDetailsTimelineViewModel(project, cs, dataContext, dataProvider)

  private val requestMoreSignal = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var loadedCompletely = false
  private val timelineLoader: StateFlow<AsyncIncrementalListComputer<GHPRTimelineItemDTO>?> =
    timelineData.timelineChangeSignal.withInitial(Unit).mapScoped {
      createComputer()
    }.stateIn(cs, SharingStarted.Eagerly, null)

  private fun CoroutineScope.createComputer(): AsyncIncrementalListComputer<GHPRTimelineItemDTO> =
    AsyncIncrementalListComputer.createIn(this, timelineData.getTimelineItems(), loadAfterDone = true).apply {
      launch(start = CoroutineStart.UNDISPATCHED) {
        requestMoreSignal.collect {
          if (it || !state.value.isComplete) {
            requestMore()
          }
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        timelineData.timelineUpdateSignal.collect {
          requestMore()
        }
      }

      // load fully if it was loaded fully at least once
      if (loadedCompletely) {
        launch(start = CoroutineStart.UNDISPATCHED) {
          state.collect {
            if (!it.isLoading && !it.isComplete) {
              requestMore()
            }
          }
        }
      }
    }

  override val loadingErrorHandler: GHLoadingErrorHandler =
    GHApiLoadingErrorHandler(project, securityService.account, GHLoginSource.PR_TIMELINE, timelineData::signalTimelineNeedsReload)

  override val commentVm: GHPRNewCommentViewModel? =
    if (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
      GHPRNewCommentViewModel(project,
                              cs,
                              commentsData,
                              viewModelWithTextCompletion)
    }
    else null

  override val htmlImageLoader = dataContext.htmlImageLoader
  override val avatarIconsProvider = dataContext.avatarIconsProvider

  private val timelineLoaderState: Flow<IncrementallyComputedValue<List<GHPRTimelineItemDTO>>> = timelineLoader.flatMapLatest {
    it?.state ?: flowOf(IncrementallyComputedValue.initial())
  }
  override val timelineItems: StateFlow<List<GHPRTimelineItem>> =
    timelineLoaderState
      .filter {
        it.isValueAvailable && !it.isLoading // don't rebuild on full reload until the first batch
        && (!loadedCompletely || it.isComplete) // don't rebuild in batches if loaded completely at least once
      }
      .onEach {
        loadedCompletely = it.isComplete
      }
      .map {
        it.valueOrNull.orEmpty()
      }
      .map {
        GHPRTimelineMergingModel().apply {
          add(it)
        }.getItemsList()
      }.mapDataToModel(
        ::getItemID,
        { createItemFromDTO(it) },
        { update(it) }
      ).stateIn(cs, SharingStarted.Eagerly, emptyList())

  override val isLoading: StateFlow<Boolean> =
    timelineLoaderState.map { it.isLoading }
      .stateIn(cs, SharingStarted.Eagerly, false)

  override val loadingError: StateFlow<Throwable?> =
    timelineLoaderState.map { it.exceptionOrNull }
      .stateIn(cs, SharingStarted.Eagerly, null)

  val showCommitRequests = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val showDiffRequests = MutableSharedFlow<ChangesSelection>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch {
      timelineLoaderState.filter {
        !it.isLoading && it.isComplete
      }.collect {
        // Update the last seen date to the last time a full timeline was loaded
        updateLastSeen(System.currentTimeMillis())
      }
    }
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
        UpdateableGHPRTimelineCommentViewModel(project,
                                               this,
                                               dataContext,
                                               dataProvider.commentsData,
                                               viewModelWithTextCompletion,
                                               data)
      }
      is GHPullRequestReview -> {
        UpdateableGHPRTimelineReviewViewModel(project, this, dataContext, dataProvider, viewModelWithTextCompletion, data).also {
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
    requestMoreSignal.tryEmit(false)
  }

  override fun update() {
    requestMoreSignal.tryEmit(true)
  }

  override fun updateAll() {
    cs.launch {
      detailsData.signalDetailsNeedReload()
      detailsData.signalMergeabilityNeedsReload()
      timelineData.signalTimelineNeedsReload()
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