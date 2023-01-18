// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.util.*

interface GitLabNoteViewModel {
  val author: GitLabUserDTO
  val createdAt: Date

  val actionsVm: GitLabNoteAdminActionsViewModel?

  val htmlBody: Flow<@Nls String>
}

class GitLabNoteViewModelImpl(
  parentCs: CoroutineScope,
  note: GitLabNote
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val author: GitLabUserDTO = note.author
  override val createdAt: Date = note.createdAt

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, note) else null

  private val body: Flow<String> = note.body
  override val htmlBody: Flow<String> = body.map { GitLabUIUtil.convertToHtml(it) }
    .shareIn(cs, SharingStarted.Lazily, 1)
}