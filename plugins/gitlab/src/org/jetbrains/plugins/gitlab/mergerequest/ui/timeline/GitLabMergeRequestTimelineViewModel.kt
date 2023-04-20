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
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestLabelEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestMilestoneEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestStateEvents
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussionsModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import java.util.concurrent.ConcurrentLinkedQueue

interface GitLabMergeRequestTimelineViewModel {
  val timelineLoadingFlow: Flow<LoadingState?>

  fun requestLoad()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val items: Flow<List<GitLabMergeRequestTimelineItemViewModel>>) : LoadingState
  }
}

private val LOG = logger<GitLabMergeRequestTimelineViewModel>()

class LoadAllGitLabMergeRequestTimelineViewModel(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private val timelineLoadingRequests = MutableSharedFlow<Unit>(1)

  private val discussionsDataProvider = GitLabMergeRequestDiscussionsModelImpl(cs, connection, mr)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val timelineLoadingFlow: Flow<LoadingState> =
    timelineLoadingRequests.transformLatest {
      emit(LoadingState.Loading)

      coroutineScope {
        val result = try {
          LoadingState.Result(createItemsFlow(this).mapToVms(this).stateIn(this))
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
      timelineLoadingRequests.emit(Unit)
    }
  }

  /**
   * Load all simple events and discussions and subscribe to user discussions changes
   */
  private suspend fun createItemsFlow(cs: CoroutineScope): Flow<List<GitLabMergeRequestTimelineItem>> {
    val api = connection.apiClient
    val project = connection.repo.repository

    val simpleEventsRequest = cs.async(Dispatchers.IO) {
      val vms = ConcurrentLinkedQueue<GitLabMergeRequestTimelineItem>()
      launch {
        discussionsDataProvider.systemDiscussions.first()
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

    return discussionsDataProvider.userDiscussions.map { discussions ->
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
      is GitLabMergeRequestTimelineItem.Immutable -> GitLabMergeRequestTimelineItemViewModel.Immutable(item)
      is GitLabMergeRequestTimelineItem.UserDiscussion -> GitLabMergeRequestTimelineItemViewModel.Discussion(cs, item.discussion)
    }
}