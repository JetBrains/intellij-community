// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.emoji

import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

interface GitLabReactionsViewModel {
  val reactions: List<String>

  fun isReactedWithCurrentUser(emoji: String): Boolean
  fun getReactionCount(emoji: String): Int
}

class GitLabReactionsViewModelImpl(
  ungroupedReactions: List<GitLabAwardEmojiDTO>,
  private val currentUser: GitLabUserDTO
) : GitLabReactionsViewModel {
  private val reactionToUsers: Map<String, List<GitLabUserDTO>> =
    ungroupedReactions.groupBy(GitLabAwardEmojiDTO::emoji, GitLabAwardEmojiDTO::user)

  override val reactions: List<String> =
    ungroupedReactions.map(GitLabAwardEmojiDTO::emoji).distinct()

  override fun isReactedWithCurrentUser(emoji: String): Boolean {
    val users = reactionToUsers[emoji] ?: return false
    return users.map(GitLabUserDTO::id).contains(currentUser.id)
  }

  override fun getReactionCount(emoji: String): Int {
    return reactionToUsers[emoji]?.size ?: 0
  }
}