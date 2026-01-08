// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabAwardEmojiRestDTO(
  val name: String,
  val user: GitLabUserRestDTO,
) {
  companion object {
    fun fromRestDTO(emojiRestDTO: GitLabAwardEmojiRestDTO, emojiMap: Map<String, String>): GitLabAwardEmojiDTO {
      return GitLabAwardEmojiDTO(
        name = emojiRestDTO.name,
        emoji = emojiMap[emojiRestDTO.name] ?: ":$emojiRestDTO.name:",
        user = GitLabUserDTO.fromRestDTO(emojiRestDTO.user)
      )
    }
  }
}
