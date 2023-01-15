// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadAllMergeRequestDiscussions
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestLabelEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestMilestoneEvents
import org.jetbrains.plugins.gitlab.mergerequest.api.request.loadMergeRequestStateEvents
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.LoadedGitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import java.util.concurrent.ConcurrentSkipListSet

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
  private var loadingRequestState = MutableStateFlow<Deferred<List<GitLabMergeRequestTimelineItemViewModel>>?>(null)

  override val timelineLoadingFlow: Flow<LoadingState?> =
    loadingRequestState.transform {
      if (it == null) {
        emit(null)
        return@transform
      }

      emit(LoadingState.Loading)
      val result = try {
        val result = it.await()
        LoadingState.Result(result)
      }
      catch (e: Exception) {
        LoadingState.Error(e)
      }
      catch (ce: CancellationException) {
        throw ce
      }
      emit(result)
    }

  @RequiresEdt
  override fun startLoading() {
    loadingRequestState.update {
      it ?: requestItemsAsync()
    }
  }

  private fun requestItemsAsync(): Deferred<List<GitLabMergeRequestTimelineItemViewModel>> =
    cs.async {
      val api = connection.apiClient
      val project = connection.repo.repository

      val result = ConcurrentSkipListSet(Comparator.comparing(GitLabMergeRequestTimelineItemViewModel::date))

      launch {
        launch {
          api.loadAllMergeRequestDiscussions(project, mr).collect { discussions ->
            result.addAll(discussions.filter {
              it.notes.isNotEmpty()
            }.map {
              if (it.notes.first().system) {
                GitLabMergeRequestTimelineItemViewModel.SystemDiscussion(it)
              }
              else {
                GitLabMergeRequestTimelineItemViewModel.Discussion(cs, LoadedGitLabDiscussion(cs, connection, it))
              }
            })
          }
        }

        launch {
          api.loadMergeRequestStateEvents(project, mr).body().let { events ->
            result.addAll(events.map { GitLabMergeRequestTimelineItemViewModel.StateEvent(it) })
          }
        }

        launch {
          api.loadMergeRequestLabelEvents(project, mr).body().let { events ->
            result.addAll(events.map { GitLabMergeRequestTimelineItemViewModel.LabelEvent(it) })
          }
        }

        launch {
          api.loadMergeRequestMilestoneEvents(project, mr).body().let { events ->
            result.addAll(events.map { GitLabMergeRequestTimelineItemViewModel.MilestoneEvent(it) })
          }
        }
      }.join()

      result.toList()
    }

  @RequiresEdt
  override fun reset() {
    loadingRequestState.update {
      it?.cancel()
      null
    }
  }
}