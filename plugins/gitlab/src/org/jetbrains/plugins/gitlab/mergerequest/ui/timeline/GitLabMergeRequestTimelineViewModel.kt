// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestLabelEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestMilestoneEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestStateEvents
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussionsModel
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussionsModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModelImpl
import java.util.concurrent.ConcurrentLinkedQueue

interface GitLabMergeRequestTimelineViewModel {
  val currentUser: GitLabUserDTO
  val timelineLoadingFlow: Flow<LoadingState?>

  val newNoteVm: Flow<NewGitLabNoteViewModel?>

  fun requestLoad()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val items: Flow<List<GitLabMergeRequestTimelineItemViewModel>>) : LoadingState
  }
}

private val LOG = logger<GitLabMergeRequestTimelineViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class LoadAllGitLabMergeRequestTimelineViewModel(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private val loadingRequests = MutableSharedFlow<Unit>(1)

  override val currentUser: GitLabUserDTO = connection.currentUser

  private val discussionsDataFlow: Flow<GitLabMergeRequestDiscussionsModel> =
    loadingRequests.mapLatest {
      val mrDetails = loadDetails()
      GitLabMergeRequestDiscussionsModelImpl(cs, connection, mrDetails)
    }.modelFlow(cs, LOG)

  override val timelineLoadingFlow: Flow<LoadingState> =
    discussionsDataFlow.transformLatest { discussions ->
      emit(LoadingState.Loading)

      coroutineScope {
        val result = try {
          LoadingState.Result(createItemsFlow(this, discussions).mapToVms(this).stateIn(this))
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Exception) {
          LoadingState.Error(e)
        }
        emit(result)
        awaitCancellation()
      }
    }.modelFlow(cs, LOG)

  @RequiresEdt
  override fun requestLoad() {
    cs.launch {
      loadingRequests.emit(Unit)
    }
  }

  private suspend fun loadDetails(): GitLabMergeRequestDTO {
    val api = connection.apiClient
    val project = connection.repo.repository
    return withContext(Dispatchers.IO) {
      api.loadMergeRequest(project, mr).body()!!
    }
  }

  override val newNoteVm: Flow<NewGitLabNoteViewModel?> =
    discussionsDataFlow.transformLatest { discussions ->
      if (discussions.canAddNotes) {
        coroutineScope {
          val editVm = NewGitLabNoteViewModelImpl(this, connection.currentUser, discussions)
          emit(editVm)
          awaitCancellation()
        }
      }
      else {
        emit(null)
      }
    }.modelFlow(cs, LOG)

  /**
   * Load all simple events and discussions and subscribe to user discussions changes
   */
  private suspend fun createItemsFlow(
    cs: CoroutineScope,
    discussionsData: GitLabMergeRequestDiscussionsModel
  ): Flow<List<GitLabMergeRequestTimelineItem>> {

    val api = connection.apiClient
    val project = connection.repo.repository

    val simpleEventsRequest = cs.async(Dispatchers.IO) {
      val vms = ConcurrentLinkedQueue<GitLabMergeRequestTimelineItem>()
      launch {
        discussionsData.systemDiscussions.first()
          .map { GitLabMergeRequestTimelineItem.SystemDiscussion(it) }
          .also { vms.addAll(it) }
      }

      launch {
        api.loadMergeRequestStateEvents(project, mr).body()
          .map { GitLabMergeRequestTimelineItem.StateEvent(it) }
          .also { vms.addAll(it) }
      }

      launch {
        api.loadMergeRequestLabelEvents(project, mr).body()
          .map { GitLabMergeRequestTimelineItem.LabelEvent(it) }
          .also { vms.addAll(it) }
      }

      launch {
        api.loadMergeRequestMilestoneEvents(project, mr).body()
          .map { GitLabMergeRequestTimelineItem.MilestoneEvent(it) }
          .also { vms.addAll(it) }
      }
      vms
    }

    return discussionsData.userDiscussions.map { discussions ->
      (simpleEventsRequest.await() + discussions.map(GitLabMergeRequestTimelineItem::UserDiscussion)).sortedBy { it.date }
    }
  }

  private suspend fun Flow<List<GitLabMergeRequestTimelineItem>>.mapToVms(cs: CoroutineScope) =
    mapCaching(
      GitLabMergeRequestTimelineItem::id,
      { createVm(cs, it) },
      { if (this is GitLabMergeRequestTimelineItemViewModel.Discussion) destroy() }
    )

  private fun createVm(cs: CoroutineScope, item: GitLabMergeRequestTimelineItem): GitLabMergeRequestTimelineItemViewModel =
    when (item) {
      is GitLabMergeRequestTimelineItem.Immutable ->
        GitLabMergeRequestTimelineItemViewModel.Immutable(item)
      is GitLabMergeRequestTimelineItem.UserDiscussion ->
        GitLabMergeRequestTimelineItemViewModel.Discussion(cs, currentUser, item.discussion)
    }
}