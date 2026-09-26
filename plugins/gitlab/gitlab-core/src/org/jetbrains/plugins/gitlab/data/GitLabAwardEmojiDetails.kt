// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.data

import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabAwardEmojiRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

data class GitLabAwardEmojiDetails(
    val id: GitLabId,
    val name: String,
    val emoji: String,
    val user: GitLabUserDTO,
) {
  companion object {
    fun fromDto(emojiRestDTO: GitLabAwardEmojiRestDTO, emojiMap: Map<String, String>): GitLabAwardEmojiDetails {
      return GitLabAwardEmojiDetails(
        emojiRestDTO.id,
        emojiRestDTO.name,
        emojiMap[emojiRestDTO.name] ?: ":$emojiRestDTO.name:",
        GitLabUserDTO.Companion.fromRestDTO(emojiRestDTO.user)
      )
    }
  }
}