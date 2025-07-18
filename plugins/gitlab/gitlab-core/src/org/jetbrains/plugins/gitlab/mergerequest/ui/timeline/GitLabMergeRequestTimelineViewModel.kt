// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.onDoneIn
import java.net.URL

interface GitLabMergeRequestTimelineViewModel : GitLabMergeRequestViewModel {
  val currentUser: GitLabUserDTO
  val showEvents: StateFlow<Boolean>
  val timelineItems: SharedFlow<Result<List<GitLabMergeRequestTimelineItemViewModel>>>
  val isLoading: SharedFlow<Boolean>

  val newNoteVm: NewGitLabNoteViewModel?

  val serverUrl: URL

  fun setShowEvents(show: Boolean)
}

private val LOG = logger<GitLabMergeRequestTimelineViewModel>()

internal class LoadAllGitLabMergeRequestTimelineViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  private val projectData: GitLabProject,
  private val preferences: GitLabMergeRequestsPreferences,
  override val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val number: String = "!${mergeRequest.iid}"
  override val author: GitLabUserDTO = mergeRequest.author
  override val title: SharedFlow<String> = mergeRequest.details.map { it.title }.map { title ->
    GitLabUIUtil.convertToHtml(project, mergeRequest.gitRepository, mergeRequest.glProject.projectPath, title)
  }.modelFlow(cs, LOG)
  override val descriptionHtml: SharedFlow<String> = mergeRequest.details.map { it.description }.map { description ->
    GitLabUIUtil.convertToHtml(project, mergeRequest.gitRepository, mergeRequest.glProject.projectPath, description)
  }.modelFlow(cs, LOG)
  override val url: String = mergeRequest.url

  private val _showEvents = MutableStateFlow(preferences.showEventsInTimeline)
  override val showEvents: StateFlow<Boolean> = _showEvents.asStateFlow()

  override val timelineItems: SharedFlow<Result<List<GitLabMergeRequestTimelineItemViewModel>>> =
    mergeRequest.createTimelineItemsFlow(showEvents)
      .transformConsecutiveSuccesses {
        mapDataToModel({ it.id }, { createItemVm(it) }, {})
      }
      .modelFlow(cs, LOG)

  override val isLoading: SharedFlow<Boolean> = combine(
    mergeRequest.isLoading,
    timelineItems.withInitial(null)
  ) { mrLoading, timelineItemsResult ->
    mrLoading || timelineItemsResult == null
  }.modelFlow(cs, LOG)

  override val newNoteVm: NewGitLabNoteViewModel? =
    if (mergeRequest.canAddNotes) {
      GitLabNoteEditingViewModel.forNewNote(cs, project, mergeRequest, currentUser).apply {
        onDoneIn(cs) {
          text.value = ""
        }
      }
    }
    else {
      null
    }

  override val serverUrl: URL = mergeRequest.glProject.serverPath.toURL()

  private val _diffRequests = MutableSharedFlow<ChangesSelection.Precise>()
  val diffRequests: Flow<ChangesSelection.Precise> = _diffRequests.asSharedFlow()

  override fun setShowEvents(show: Boolean) {
    _showEvents.value = show
    preferences.showEventsInTimeline = show
  }

  override fun reloadData() {
    cs.launchNow {
      mergeRequest.reloadData()
    }
  }

  override fun refreshData() {
    cs.launchNow {
      mergeRequest.refreshData()
    }
  }

  /**
   * Load all simple events and discussions and subscribe to user discussions changes
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun GitLabMergeRequest.createTimelineItemsFlow(showEventsFlow: Flow<Boolean>): Flow<Result<List<GitLabMergeRequestTimelineItem>>> =
    showEventsFlow.flatMapLatest { showEvents ->
      val simpleEvents: Flow<Result<List<GitLabMergeRequestTimelineItem.Immutable>>> =
        if (!showEvents) {
          flowOf(Result.success(emptyList()))
        }
        else {
          combine(stateEvents, labelEvents, milestoneEvents, systemNotes) { stateResult, labelsResult, milesResult, systemNotesResult ->
            val state = stateResult.getOrElse { return@combine Result.failure(it) }
            val labels = labelsResult.getOrElse { return@combine Result.failure(it) }
            val miles = milesResult.getOrElse { return@combine Result.failure(it) }
            val systemNotes = systemNotesResult.getOrElse { return@combine Result.failure(it) }

            Result.success(state.map(GitLabMergeRequestTimelineItem::StateEvent) +
                           labels.map(GitLabMergeRequestTimelineItem::LabelEvent) +
                           miles.map(GitLabMergeRequestTimelineItem::MilestoneEvent) +
                           systemNotes.map(GitLabMergeRequestTimelineItem::SystemNote))
          }
        }

      combine(simpleEvents, discussions, draftNotes) { eventsResult, discussionsResult, draftNotesResult ->
        val events = eventsResult.getOrElse { return@combine Result.failure(it) }
        val discussions = discussionsResult.getOrElse { return@combine Result.failure(it) }
        val draftNotes = draftNotesResult.getOrElse { return@combine Result.failure(it) }.filter { it.discussionId == null }

        val timeline = (events +
                        discussions.map(GitLabMergeRequestTimelineItem::UserDiscussion)
                       ).sortedBy { it.date } + draftNotes.map(GitLabMergeRequestTimelineItem::DraftNote)

        Result.success(timeline)
      }
    }

  private fun CoroutineScope.createItemVm(item: GitLabMergeRequestTimelineItem)
    : GitLabMergeRequestTimelineItemViewModel =
    when (item) {
      is GitLabMergeRequestTimelineItem.Immutable ->
        GitLabMergeRequestTimelineItemViewModel.Immutable.fromModel(project, mergeRequest, item)
      is GitLabMergeRequestTimelineItem.UserDiscussion ->
        GitLabMergeRequestTimelineItemViewModel.Discussion(project, cs, projectData, currentUser, mergeRequest, item.discussion).also {
          handleDiffRequests(it.diffVm, _diffRequests::emit)
        }
      is GitLabMergeRequestTimelineItem.DraftNote ->
        GitLabMergeRequestTimelineItemViewModel.DraftNote(project, cs, mergeRequest, item.note).also {
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