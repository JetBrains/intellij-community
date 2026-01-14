// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.data.GitLabAwardEmoji

interface GitLabReaction {
  val name: String
  val emoji: String
  val category: String?
}

class GitLabReactionImpl private constructor(
  override val name: String,
  override val emoji: String,
  override val category: String? = null
) : GitLabReaction {

  constructor(model: GitLabAwardEmoji) : this(model.name, model.emoji)
  constructor(parsedEmoji: ParsedGitLabEmoji) : this(parsedEmoji.name, parsedEmoji.moji, parsedEmoji.category)

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