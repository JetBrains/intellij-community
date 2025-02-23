// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
  private val cs = parentCs.childScope("GitHub Reactions View Model")

  private val reactionsState = MutableStateFlow(reactionsFlow.value)

  init {
    cs.launchNow {
      reactionsFlow.collect(reactionsState)
    }
  }

  override val reactionsWithInfo: StateFlow<Map<GHReactionContent, ReactionInfo>> = reactionsState.map { data ->
    val reactionToUsers = data.groupBy({ it.content }, { it.user })
    reactionToUsers.mapValues { (_, usersNullable) ->
      val users = usersNullable.filterNotNull()
      ReactionInfo(users, users.map(GHUser::id).contains(currentUser.id))
    }
  }.stateInNow(cs, emptyMap())

  override fun toggle(reactionContent: GHReactionContent) {
    cs.launch {
      val isReacted = reactionsWithInfo.value[reactionContent]?.isReactedByCurrentUser ?: false
      if (isReacted) {
        val reaction = reactionsService.removeReaction(reactableId, reactionContent)
        reactionsState.update { it - reaction }
      }
      else {
        val reaction = reactionsService.addReaction(reactableId, reactionContent)
        reactionsState.update { it + reaction }
      }
    }
  }
}

data class ReactionInfo(
  val users: List<GHUser>,
  val isReactedByCurrentUser: Boolean
)