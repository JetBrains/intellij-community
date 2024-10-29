// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNote
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReaction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabReactionImpl

interface GitLabReactionsViewModel {
  val availableReactions: StateFlow<ComputedResult<List<GitLabReaction>>?>
  val reactionsWithInfo: StateFlow<Map<GitLabReaction, ReactionInfo>>

  fun toggle(reaction: GitLabReaction)
}

private val LOG = logger<GitLabReactionsViewModelImpl>()

class GitLabReactionsViewModelImpl(
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  private val note: GitLabMergeRequestNote,
  private val currentUser: GitLabUserDTO
) : GitLabReactionsViewModel {
  private val cs = parentCs.childScope("GitLab Reactions View Model")

  override val availableReactions: StateFlow<ComputedResult<List<GitLabReaction>>?> =
    computationStateFlow(flowOf(Unit)) {
      try {
        projectData.getEmojis()
      }
      catch (e: Exception) {
        if (e !is CancellationException) {
          LOG.warn("Failed to load available reactions", e)
        }
        throw e
      }
    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val reactionsWithInfo: StateFlow<Map<GitLabReaction, ReactionInfo>> = note.awardEmoji.mapState(cs) { data ->
    val reactionToUsers = data.groupBy({ dto -> GitLabReactionImpl(dto) }, GitLabAwardEmojiDTO::user)
    reactionToUsers.mapValues { (_, users) ->
      ReactionInfo(users, users.map(GitLabUserDTO::id).contains(currentUser.id))
    }
  }

  override fun toggle(reaction: GitLabReaction) {
    cs.launch {
      note.toggleReaction(reaction)
    }
  }
}

data class ReactionInfo(
  val users: List<GitLabUserDTO>,
  val isReactedByCurrentUser: Boolean
)