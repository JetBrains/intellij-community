// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.1")
@GraphQLFragment("/graphql/fragment/awardEmoji.graphql")
data class GitLabAwardEmojiDTO(
  val name: String,
  val emoji: String,
  val user: GitLabUserDTO
)