// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

data class GitLabAwardEmoji(
  val id: GitLabId,
  val name: String,
  val emoji: String,
  val user: GitLabUserDTO,
) {
  companion object {
    fun fromDto(emojiRestDTO: GitLabAwardEmojiRestDTO, emojiMap: Map<String, String>): GitLabAwardEmoji {
      return GitLabAwardEmoji(
        emojiRestDTO.id,
        emojiRestDTO.name,
        emojiMap[emojiRestDTO.name] ?: ":$emojiRestDTO.name:",
        GitLabUserDTO.fromRestDTO(emojiRestDTO.user)
      )
    }
  }
}
