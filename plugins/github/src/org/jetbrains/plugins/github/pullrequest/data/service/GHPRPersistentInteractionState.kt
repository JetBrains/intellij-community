// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.time.Duration
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "GitHubPullRequestState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GHPRPersistentInteractionState : SerializablePersistentStateComponent<GHPRPersistentInteractionState.State>(State(listOf())) {
  companion object {
    private const val CLEAR_AFTER_DAYS_KEY = "github.clear.last.seen.state.days"
  }

  @Serializable
  data class PRState(
    val id: GHPRIdentifier,
    val lastSeen: Long? = null
  )

  @Serializable
  data class State(val prStates: List<PRState>)

  fun isSeen(pr: GHPullRequestShort): Boolean {
    val lastSeen = state.prStates.find { it.id == pr.prId }?.lastSeen
    val isSeen = (lastSeen != null && Date(lastSeen) >= pr.updatedAt)

    // TODO: Revise this check when adding a new-in-timeline line.
    // Cleanup state entries for PRs that have updates
    val clearBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(CLEAR_AFTER_DAYS_KEY).toLong()).toMillis()
    if (!isSeen && lastSeen != null && lastSeen < clearBeforeTime) {
      updateState { st -> st.copy(prStates = st.prStates.filterNot { it.id == pr.prId }) }
    }

    return isSeen
  }

  fun updateStateFor(prId: GHPRIdentifier, updater: (PRState?) -> PRState) {
    updateState { st ->
      val prStatesExcludingCurrent = st.prStates.filterNot { it.id == prId }
      val addedState = updater(st.prStates.find { it.id == prId })
      st.copy(prStates = prStatesExcludingCurrent + listOf(addedState))
    }
  }
}