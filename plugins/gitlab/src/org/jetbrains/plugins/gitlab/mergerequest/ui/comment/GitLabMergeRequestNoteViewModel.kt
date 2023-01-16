// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.util.*

interface GitLabMergeRequestNoteViewModel {
  val author: GitLabUserDTO
  val createdAt: Date

  val htmlBody: Flow<@Nls String>
}

class LoadedGitLabMergeRequestNoteViewModel(
  parentCs: CoroutineScope,
  note: GitLabNoteDTO
) : GitLabMergeRequestNoteViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val author: GitLabUserDTO = note.author
  override val createdAt: Date = note.createdAt

  private val body = MutableStateFlow(note.body)
  override val htmlBody: Flow<String> = body.map { GitLabUIUtil.convertToHtml(it) }
    .shareIn(cs, SharingStarted.Lazily, 1)
}