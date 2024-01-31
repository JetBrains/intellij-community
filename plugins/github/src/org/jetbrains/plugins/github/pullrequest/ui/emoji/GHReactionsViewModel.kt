// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.GHReactionsService

interface GHReactionsViewModel {
  val reactionIconsProvider: IconsProvider<GHReactionContent>
  val reactionsWithInfo: StateFlow<Map<GHReactionContent, ReactionInfo>>

  fun toggle(reactionContent: GHReactionContent)
}

internal class GHReactionViewModelImpl(
  parentCs: CoroutineScope,
  private val reactableId: String,
  reactionsFlow: StateFlow<List<GHReaction>>,
  private val currentUser: GHUser,
  private val reactionsService: GHReactionsService,
  override val reactionIconsProvider: IconsProvider<GHReactionContent>
) : GHReactionsViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Reactions View Model"))

  private val localData = MutableStateFlow(reactionsFlow.value)
  private val dataState = callbackFlow {
    launch { localData.collect(::send) }
    launch { reactionsFlow.collect(::send) }
    awaitCancellation()
  }.stateInNow(cs, emptyList())

  override val reactionsWithInfo: StateFlow<Map<GHReactionContent, ReactionInfo>> = dataState.mapState(cs) { data ->
    val reactionToUsers = data.groupBy({ it.content }, { it.user })
    reactionToUsers.mapValues { (_, users) ->
      ReactionInfo(users.size, users.map(GHUser::id).contains(currentUser.id))
    }
  }

  override fun toggle(reactionContent: GHReactionContent) {
    cs.launch {
      val isReacted = reactionsWithInfo.value[reactionContent]?.isReactedByCurrentUser ?: false
      if (isReacted) {
        val reaction = reactionsService.removeReaction(reactableId, reactionContent)
        updateDataLocally(reaction, ReactionRequest.REMOVE)
      }
      else {
        val reaction = reactionsService.addReaction(reactableId, reactionContent)
        updateDataLocally(reaction, ReactionRequest.ADD)
      }
    }
  }

  private fun updateDataLocally(reaction: GHReaction, request: ReactionRequest) {
    localData.update {
      val updatedData = it.toMutableList()
      when (request) {
        ReactionRequest.ADD -> updatedData.add(reaction)
        ReactionRequest.REMOVE -> updatedData.remove(reaction)
      }

      updatedData
    }
  }
}

private enum class ReactionRequest {
  ADD,
  REMOVE
}

data class ReactionInfo(
  val count: Int,
  val isReactedByCurrentUser: Boolean
)