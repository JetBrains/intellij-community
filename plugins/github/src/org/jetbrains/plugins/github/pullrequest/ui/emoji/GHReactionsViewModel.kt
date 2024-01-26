// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.emoji

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.GHUser

private val LOG = logger<GHReactionsViewModel>()

interface GHReactionsViewModel {
  val reactionIconsProvider: IconsProvider<GHReactionContent>
  val reactions: SharedFlow<List<GHReaction>>

  fun isReactedWithCurrentUser(reaction: GHReaction): Boolean
  fun getReactionCount(reaction: GHReaction): Int
}

internal class GHReactionViewModelImpl(
  parentCs: CoroutineScope,
  ungroupedReactions: List<GHReaction>,
  private val currentUser: GHUser,
  override val reactionIconsProvider: IconsProvider<GHReactionContent>
) : GHReactionsViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Reactions View Model"))

  private val dataState: StateFlow<List<GHReaction>> = MutableStateFlow(ungroupedReactions.toMutableList())
  private val reactionsToUsers: StateFlow<Map<GHReactionContent, List<GHUser>>> = dataState.mapState(cs) {
    it.groupBy(GHReaction::content, GHReaction::user)
  }
  override val reactions: SharedFlow<List<GHReaction>> = dataState.map { it.distinctBy(GHReaction::content) }
    .modelFlow(cs, LOG)

  override fun isReactedWithCurrentUser(reaction: GHReaction): Boolean {
    val users = reactionsToUsers.value[reaction.content] ?: return false
    return users.map(GHUser::id).contains(currentUser.id)
  }

  override fun getReactionCount(reaction: GHReaction): Int {
    return reactionsToUsers.value[reaction.content]?.size ?: 0
  }
}