// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.AIReviewCollector
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.Change
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class ReviewRating { None, Like, Dislike }

@ApiStatus.Internal
class AIReviewViewModel(
  private val project: Project,
  initialRequest: AIReviewRequest,
) : AIReviewStateHandler {

  internal val rating = MutableStateFlow(ReviewRating.None)

  private val latestRequest = MutableStateFlow(initialRequest)
  private val runningReviewActivity = MutableStateFlow<StructuredIdeActivity?>(null)

  val state: MutableStateFlow<State> = MutableStateFlow<State>(State.NotStarted)

  val problems: StateFlow<List<AIReviewResult.Problem>> = state
    .mapState { state ->
      (state as? State.FullReviewReceived)?.fullReview?.problems
      ?: (state as? State.PartialReviewReceived)?.partialReview?.problems
      ?: emptyList()
    }

  val changes: StateFlow<List<Change>> = latestRequest
    .mapState { request ->
      (request as? AIReviewRequest.LocalChanges)?.changes.orEmpty()
    }

  override fun getCurrentRequest(): AIReviewRequest = latestRequest.value

  override fun setErrorState(e: Throwable?, partialReview: AIReviewResult?) {
    val message = e?.message
    val errorMessage =
      if (!message.isNullOrEmpty()) AIReviewBundle.message("aiReview.problems.fail.received", message)
      else AIReviewBundle.message("aiReview.problems.no.response.received")

    val request = (state.value as? State.RequestHolder)?.request
    val partialResult = if (partialReview == null && request != null) AIReviewResult(request, emptyList()) else partialReview
    setNewState(State.Error(errorMessage, partialResult))
  }

  override fun setPartialReviewState(partialReview: AIReviewResult) {
    setNewState(State.PartialReviewReceived(partialReview))
  }

  override fun setFullReviewState(fullReview: AIReviewResult) {
    setNewState(State.FullReviewReceived(fullReview))
  }

  override fun setCancelledState(partialReview: AIReviewResult?) {
    setNewState(State.Cancelled(partialReview))
  }

  override fun setFiltersAppliedState(filterName: String, state: Boolean) {
    setNewState(State.FilterApplied(filterName, state, getCurrentRequest()))
  }

  fun setRunningState(request: AIReviewRequest, cancel: () -> Unit) {
    latestRequest.update { request }
    setNewState(State.Running(request, cancel))
  }

  private fun setNewState(newState: State) {
    state.update { newState }
    ApplicationManager.getApplication().executeOnPooledThread {
      logReviewActivity(newState)
    }
  }

  private fun logReviewActivity(newState: State) {
    if (newState is State.Running) {
      runningReviewActivity.getAndUpdate { activity ->
        if (activity != null && !activity.isFinished()) {
          AIReviewCollector.logReviewComplete(activity, newState)
        }
        AIReviewCollector.logReviewStarted(project, this)
      }
    }
    else if (newState is State.WithFullReview
             || newState is State.Cancelled
             || newState is State.Error) {
      runningReviewActivity.getAndUpdate { activity ->
        if (activity != null && !activity.isFinished()) {
          AIReviewCollector.logReviewComplete(activity, newState)
        }
        null
      }
    }
  }

  sealed interface State {
    interface RequestHolder {
      val request: AIReviewRequest?
    }

    object NotStarted : State

    interface WithPartialReview : RequestHolder {
      override val request: AIReviewRequest? get() = partialReview?.request
      val partialReview: AIReviewResult?
    }

    interface WithFullReview : RequestHolder {
      override val request: AIReviewRequest get() = fullReview.request
      val fullReview: AIReviewResult
    }

    class Running(override val request: AIReviewRequest, val cancel: () -> Unit) : State, RequestHolder

    data class PartialReviewReceived(override val partialReview: AIReviewResult) : State, WithPartialReview
    data class FullReviewReceived(override val fullReview: AIReviewResult) : State, WithFullReview

    data class Error(@NlsContexts.Label val message: String, override val partialReview: AIReviewResult?) : State, WithPartialReview
    data class Cancelled(override val partialReview: AIReviewResult?) : State, WithPartialReview

    data class FilterApplied(val filterName: String, val state: Boolean, override val request: AIReviewRequest?) : State, RequestHolder
  }
}

/**
 * Simple [StateFlow] mapping without requiring a [CoroutineScope].
 * Uses a lazy derivation approach compatible with the community module dependencies.
 */
private fun <T, M> StateFlow<T>.mapState(mapper: (value: T) -> M): StateFlow<M> =
  MappedStateFlow(this, mapper)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<T, R>(
  private val source: StateFlow<T>,
  private val mapper: (T) -> R,
) : StateFlow<R> {
  override val value: R
    get() = mapper(source.value)

  override val replayCache: List<R>
    get() = source.replayCache.map(mapper)

  override suspend fun collect(collector: FlowCollector<R>): Nothing {
    source.map(mapper).distinctUntilChanged().collect(collector)
    awaitCancellation()
  }
}
