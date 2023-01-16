// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapScoped
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
import java.util.*

interface GitLabMergeRequestTimelineViewModel {
  val timelineLoadingFlow: Flow<LoadingState?>

  fun startLoading()
  fun reset()

  sealed interface LoadingState {
    object Loading : LoadingState
    class Error(val exception: Throwable) : LoadingState
    class Result(val items: List<GitLabMergeRequestTimelineItemViewModel>) : LoadingState
  }
}

class LoadAllGitLabMergeRequestTimelineViewModel(
  parentCs: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val mr: GitLabMergeRequestId
) : GitLabMergeRequestTimelineViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)
  private var loadingRequestState = MutableStateFlow<Flow<List<GitLabMergeRequestTimelineItemViewModel>>?>(null)

  private val discussionsDataProvider = GitLabMergeRequestDiscussionsModelImpl(cs, connection, mr)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val timelineLoadingFlow: Flow<LoadingState?> =
    loadingRequestState.transformLatest { itemsFlow ->
      if (itemsFlow == null) {
        emit(null)
        return@transformLatest
      }

      emit(LoadingState.Loading)

      // so it's not a supervisor
      coroutineScope {
        itemsFlow.catch {
          emit(LoadingState.Error(it))
        }.collectLatest {
          emit(LoadingState.Result(it))
        }
      }
    }

  @RequiresEdt
  override fun startLoading() {
    loadingRequestState.update {
      it ?: createItemsFlow()
    }
  }

  private fun createItemsFlow(): Flow<List<GitLabMergeRequestTimelineItemViewModel>> {
    val api = connection.apiClient
    val project = connection.repo.repository

    val discussionsFlow = discussionsDataProvider.userDiscussions.mapScoped { discussions ->
      val discussionsScope = this
      discussions.map {
        GitLabMergeRequestTimelineItemViewModel.Discussion(discussionsScope, it)
      }
    }

    val simpleEventsFlow: Flow<List<GitLabMergeRequestTimelineItemViewModel.Immutable>> =
      channelFlow {
        launch {
          discussionsDataProvider.systemDiscussions.collect { discussions ->
            send(discussions.map { GitLabMergeRequestTimelineItemViewModel.SystemDiscussion(it) })
          }
        }

        launch {
          api.loadMergeRequestStateEvents(project, mr).body().let { events ->
            events.map { GitLabMergeRequestTimelineItemViewModel.StateEvent(it) }
          }.also {
            send(it)
          }
        }

        launch {
          api.loadMergeRequestStateEvents(project, mr).body().let { events ->
            events.map { GitLabMergeRequestTimelineItemViewModel.StateEvent(it) }
          }.also {
            send(it)
          }
        }

        launch {
          api.loadMergeRequestLabelEvents(project, mr).body().let { events ->
            events.map { GitLabMergeRequestTimelineItemViewModel.LabelEvent(it) }
          }.also {
            send(it)
          }
        }

        launch {
          api.loadMergeRequestMilestoneEvents(project, mr).body().let { events ->
            events.map { GitLabMergeRequestTimelineItemViewModel.MilestoneEvent(it) }
          }.also {
            send(it)
          }
        }
      }.flowOn(Dispatchers.IO)


    return combine(discussionsFlow, simpleEventsFlow) { discussions, simpleEvents ->
      TreeSet(Comparator.comparing(GitLabMergeRequestTimelineItemViewModel::date)).apply {
        addAll(simpleEvents)
        addAll(discussions)
      }.toList()
    }
  }

  @RequiresEdt
  override fun reset() {
    loadingRequestState.value = null
  }
}