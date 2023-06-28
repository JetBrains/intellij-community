// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.launchNow
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
import org.jetbrains.plugins.gitlab.mergerequest.diff.ChangesSelection
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel
import org.jetbrains.plugins.gitlab.ui.comment.DelegatingGitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.forNewNote
import org.jetbrains.plugins.gitlab.ui.comment.onDoneIn

interface GitLabMergeRequestTimelineViewModel : GitLabMergeRequestViewModel {
  val currentUser: GitLabUserDTO
  val timelineItems: Flow<List<GitLabMergeRequestTimelineItemViewModel>>

  val newNoteVm: NewGitLabNoteViewModel?

  fun requestLoad()
}

private val LOG = logger<GitLabMergeRequestTimelineViewModel>()

class LoadAllGitLabMergeRequestTimelineViewModel(
  parentCs: CoroutineScope,
  override val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private val loadingRequests = MutableSharedFlow<Unit>(1)

  override val number: String = "!${mergeRequest.number}"
  override val author: GitLabUserDTO = mergeRequest.author
  override val title: Flow<String> = mergeRequest.title
  override val descriptionHtml: Flow<String> = mergeRequest.descriptionHtml
  override val url: String = mergeRequest.url

  override val timelineItems: Flow<List<GitLabMergeRequestTimelineItemViewModel>> =
    mergeRequest.createTimelineItemsFlow().mapToVms(mergeRequest).modelFlow(cs, LOG)

  @RequiresEdt
  override fun requestLoad() {
    cs.launch {
      loadingRequests.emit(Unit)
    }
  }

  override val newNoteVm: NewGitLabNoteViewModel? =
    if (mergeRequest.canAddNotes) {
      DelegatingGitLabNoteEditingViewModel(cs, "", mergeRequest::addNote).forNewNote(currentUser).apply {
        onDoneIn(cs) {
          text.value = ""
        }
      }
    }
    else {
      null
    }

  private val _diffRequests = MutableSharedFlow<ChangesSelection.Precise>()
  val diffRequests: Flow<ChangesSelection.Precise> = _diffRequests.asSharedFlow()

  override fun refreshData() {
    cs.launchNow {
      mergeRequest.refreshData()
    }
  }

  /**
   * Load all simple events and discussions and subscribe to user discussions changes
   * TODO: handle event loading errors
   */
  private fun GitLabMergeRequest.createTimelineItemsFlow(): Flow<List<GitLabMergeRequestTimelineItem>> {
    val simpleEvents: Flow<List<GitLabMergeRequestTimelineItem.Immutable>> =
      combine(stateEvents, labelEvents, milestoneEvents) { state, labels, miles ->
        state.map(GitLabMergeRequestTimelineItem::StateEvent) +
        labels.map(GitLabMergeRequestTimelineItem::LabelEvent) +
        miles.map(GitLabMergeRequestTimelineItem::MilestoneEvent)
      }

    val standaloneDraftNotes = draftNotes.mapFiltered { it.discussionId == null }
    return combine(simpleEvents, systemNotes, discussions, standaloneDraftNotes) { events, systemNotes, discussions, draftNotes ->
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
      { item -> createItemVm(mr, item) },
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
  handler: suspend (ChangesSelection.Precise) -> Unit
) {
  launch(start = CoroutineStart.UNDISPATCHED) {
    diffVm
      .filterNotNull()
      .flatMapLatest { it.showDiffRequests }
      .collectLatest(handler)
  }
}