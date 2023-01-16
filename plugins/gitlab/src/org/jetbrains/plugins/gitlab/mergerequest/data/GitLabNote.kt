// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.util.*

interface GitLabNote {
  val author: GitLabUserDTO
  val createdAt: Date

  val body: Flow<String>
}

class LoadedGitLabNote(
  note: GitLabNoteDTO
) : GitLabNote {

  override val author: GitLabUserDTO = note.author
  override val createdAt: Date = note.createdAt

  override val body: Flow<String> = flowOf(note.body)
}
