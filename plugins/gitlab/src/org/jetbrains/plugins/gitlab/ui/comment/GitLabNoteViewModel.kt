// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.util.*

interface GitLabNoteViewModel {
  val id: String
  val author: GitLabUserDTO
  val createdAt: Date

  val discussionState: Flow<GitLabDiscussionStateContainer>

  val actionsVm: GitLabNoteAdminActionsViewModel?

  val htmlBody: Flow<@Nls String>
}

private val LOG = logger<GitLabNoteViewModel>()

class GitLabNoteViewModelImpl(
  parentCs: CoroutineScope,
  note: GitLabNote,
  override val discussionState: Flow<GitLabDiscussionStateContainer>
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val id: String = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date = note.createdAt

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, note) else null

  private val body: Flow<String> = note.body
  override val htmlBody: Flow<String> = body.map { GitLabUIUtil.convertToHtml(it) }.modelFlow(cs, LOG)

  suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }
}