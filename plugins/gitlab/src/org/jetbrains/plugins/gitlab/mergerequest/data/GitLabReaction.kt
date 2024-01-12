// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiDTO

interface GitLabReaction {
  val name: String
  val emoji: String
}

class GitLabReactionImpl(dto: GitLabAwardEmojiDTO) : GitLabReaction {
  override val name: String = dto.name
  override val emoji: String = dto.emoji

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabReactionImpl) return false

    if (name != other.name) return false

    return true
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }
}