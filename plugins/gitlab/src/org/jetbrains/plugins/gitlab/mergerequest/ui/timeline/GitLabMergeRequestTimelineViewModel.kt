// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.mapFiltered
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import org.jetbrains.plugins.gitlab.ui.comment.DelegatingGitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.forNewNote
import org.jetbrains.plugins.gitlab.ui.comment.onDoneIn

interface GitLabMergeRequestTimelineViewModel {
  val currentUser: GitLabUserDTO
  val timelineLoadingFlow: Flow<LoadingState?>

  val newNoteVm: Flow<NewGitLabNoteViewModel?>

  fun requestLoad()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(
      val mr: GitLabMergeRequest,
      val items: Flow<List<GitLabMergeRequestTimelineItemViewModel>>
    ) : LoadingState
  }
}

private val LOG = logger<GitLabMergeRequestTimelineViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class LoadAllGitLabMergeRequestTimelineViewModel(
  parentCs: CoroutineScope,
  override val currentUser: GitLabUserDTO,
  project: GitLabProject,
  mrId: GitLabMergeRequestId
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private val mrStore = project.mergeRequests
  private val loadingRequests = MutableSharedFlow<Unit>(1)

  private val mergeRequestFlow: Flow<Result<GitLabMergeRequest>> = loadingRequests.flatMapLatest {
    mrStore.getShared(mrId)
  }.modelFlow(cs, LOG)

  override val timelineLoadingFlow: Flow<LoadingState> = channelFlow {
    mergeRequestFlow.collectLatest { mrResult ->
      send(LoadingState.Loading)
      coroutineScope {
        val result = try {
          val mr = mrResult.getOrThrow()
          LoadingState.Result(mr, createItemsFlow(mr).mapToVms(mr).stateIn(this))
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Exception) {
          LoadingState.Error(e)
        }
        send(result)
        awaitCancellation()
      }
    }
  }.modelFlow(cs, LOG)

  @RequiresEdt
  override fun requestLoad() {
    cs.launch {
      loadingRequests.emit(Unit)
    }
  }

  override val newNoteVm: Flow<NewGitLabNoteViewModel?> =
    mergeRequestFlow.transformLatest {
      val discussions = it.getOrNull()
      if (discussions != null && discussions.canAddNotes) {
        coroutineScope {
          val cs = this
          val editVm = DelegatingGitLabNoteEditingViewModel(cs, "", discussions::addNote).forNewNote(currentUser).apply {
            onDoneIn(cs) {
              text.value = ""
            }
          }
          emit(editVm)
          awaitCancellation()
        }
      }
      else {
        emit(null)
      }
    }.modelFlow(cs, LOG)

  private val _diffRequests = MutableSharedFlow<GitLabDiscussionDiffViewModel.FullDiffRequest>()
  val diffRequests: Flow<GitLabDiscussionDiffViewModel.FullDiffRequest> = _diffRequests.asSharedFlow()

  /**
   * Load all simple events and discussions and subscribe to user discussions changes
   */
  private fun createItemsFlow(mr: GitLabMergeRequest): Flow<List<GitLabMergeRequestTimelineItem>> {
    val simpleEvents: Flow<List<GitLabMergeRequestTimelineItem.Immutable>> =
      combine(mr.stateEvents, mr.labelEvents, mr.milestoneEvents) { state, labels, miles ->
        state.map(GitLabMergeRequestTimelineItem::StateEvent) +
        labels.map(GitLabMergeRequestTimelineItem::LabelEvent) +
        miles.map(GitLabMergeRequestTimelineItem::MilestoneEvent)
      }

    val standaloneDraftNotes = mr.draftNotes.mapFiltered { it.discussionId == null }
    return combine(simpleEvents, mr.systemNotes, mr.discussions, standaloneDraftNotes) { events, systemNotes, discussions, draftNotes ->
      (events +
       systemNotes.map { GitLabMergeRequestTimelineItem.SystemNote(it) } +
       discussions.map(GitLabMergeRequestTimelineItem::UserDiscussion)
      ).sortedBy { it.date } +
      draftNotes.map(GitLabMergeRequestTimelineItem::DraftNote)
    }
  }

  private fun Flow<List<GitLabMergeRequestTimelineItem>>.mapToVms(mr: GitLabMergeRequest) =
    mapCaching(
      GitLabMergeRequestTimelineItem::id,
      { cs, item -> cs.createItemVm(mr, item) },
      { if (this is GitLabMergeRequestTimelineItemViewModel.Discussion) destroy() }
    )

  private fun CoroutineScope.createItemVm(mr: GitLabMergeRequest, item: GitLabMergeRequestTimelineItem)
    : GitLabMergeRequestTimelineItemViewModel =
    when (item) {
      is GitLabMergeRequestTimelineItem.Immutable ->
        GitLabMergeRequestTimelineItemViewModel.Immutable(item)
      is GitLabMergeRequestTimelineItem.UserDiscussion ->
        GitLabMergeRequestTimelineItemViewModel.Discussion(cs, currentUser, mr, item.discussion).also {
          handleDiffRequests(it.diffVm, _diffRequests::emit)
        }
      is GitLabMergeRequestTimelineItem.DraftNote ->
        GitLabMergeRequestTimelineItemViewModel.DraftDiscussion(cs, currentUser, mr, item.note).also {
          handleDiffRequests(it.diffVm, _diffRequests::emit)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.handleDiffRequests(
  diffVm: Flow<GitLabDiscussionDiffViewModel?>,
  handler: suspend (GitLabDiscussionDiffViewModel.FullDiffRequest) -> Unit
) {
  launch(start = CoroutineStart.UNDISPATCHED) {
    diffVm
      .filterNotNull()
      .flatMapLatest { it.showDiffRequests }
      .collectLatest(handler)
  }
}