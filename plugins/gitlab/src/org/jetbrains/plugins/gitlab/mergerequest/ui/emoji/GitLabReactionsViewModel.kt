// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import com.intellij.collaboration.async.mapState
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*

interface GitLabReactionsViewModel {
  val availableParsedEmojis: Deferred<List<ParsedGitLabEmoji>>
  val reactionsWithInfo: StateFlow<Map<GitLabReaction, ReactionInfo>>

  fun toggle(reaction: GitLabReaction)
}

class GitLabReactionsViewModelImpl(
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  private val note: GitLabMergeRequestNote,
  private val currentUser: GitLabUserDTO
) : GitLabReactionsViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitLab Reactions View Model"))

  override val availableParsedEmojis: Deferred<List<ParsedGitLabEmoji>> = projectData.emojis

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