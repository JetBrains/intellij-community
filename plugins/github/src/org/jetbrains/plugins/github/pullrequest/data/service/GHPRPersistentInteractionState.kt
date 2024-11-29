// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.time.Duration
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "GitHubPullRequestState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class GHPRPersistentInteractionState : SerializablePersistentStateComponent<GHPRPersistentInteractionState.State>(State(listOf())) {
  companion object {
    private const val CLEAR_AFTER_DAYS_KEY = "github.clear.last.seen.state.days"
    private const val MARGIN_MILLIS_KEY = "github.last.seen.state.margin.millis"
  }

  @Serializable
  data class PRState(
    val id: GHPRIdentifier,
    val lastSeen: Long? = null,
  )

  @Serializable
  data class State(val prStates: List<PRState>)

  private val stateFlow = MutableStateFlow(state)
  val updateSignal: Flow<Unit> = stateFlow.map { }

  fun isSeen(pr: GHPullRequestShort, currentUser: GHUser): Boolean {
    if (pr.author?.id != currentUser.id && pr.reviewRequests.none { it.requestedReviewer?.id == currentUser.id }) {
      return true
    }

    val lastSeen = state.prStates.find { it.id == pr.prId }?.lastSeen
    val isSeen = (lastSeen != null && Date(lastSeen + Registry.intValue(MARGIN_MILLIS_KEY)) >= pr.updatedAt)

    // TODO: Revise this check when adding a new-in-timeline line.
    // Cleanup state entries for PRs that have updates
    val clearBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(CLEAR_AFTER_DAYS_KEY).toLong()).toMillis()
    if (!isSeen && lastSeen != null && lastSeen < clearBeforeTime) {
      updateStateAndEmit { st -> st.copy(prStates = st.prStates.filterNot { it.id == pr.prId }) }
    }

    return isSeen
  }

  fun updateStateFor(prId: GHPRIdentifier, updater: (PRState?) -> PRState) {
    updateStateAndEmit { st ->
      val prStatesExcludingCurrent = st.prStates.filterNot { it.id == prId }
      val addedState = updater(st.prStates.find { it.id == prId })
      st.copy(prStates = prStatesExcludingCurrent + listOf(addedState))
    }
  }

  private fun updateStateAndEmit(updater: (State) -> State) {
    updateState { st ->
      updater(st)
    }.also {
      stateFlow.value = it
    }
  }
}