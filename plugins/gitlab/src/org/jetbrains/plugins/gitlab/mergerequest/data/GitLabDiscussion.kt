// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import java.util.*

interface GitLabDiscussion {
  val createdAt: Date
  val notes: Flow<List<GitLabNoteDTO>>
}

class LoadedGitLabDiscussion(
  discussion: GitLabDiscussionDTO
) : GitLabDiscussion {
  init {
    require(discussion.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  override val createdAt: Date = discussion.createdAt

  override val notes: Flow<List<GitLabNoteDTO>> = MutableStateFlow(discussion.notes)
}