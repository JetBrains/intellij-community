// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.asSafely
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.util.*

interface GitLabNoteViewModel {
  val id: String
  val author: GitLabUserDTO
  val createdAt: Date?
  val isDraft: Boolean

  val discussionState: Flow<GitLabDiscussionStateContainer>

  val actionsVm: GitLabNoteAdminActionsViewModel?

  val body: Flow<@Nls String>
  val bodyHtml: Flow<@Nls String>
}

private val LOG = logger<GitLabNoteViewModel>()

class GitLabNoteViewModelImpl(
  parentCs: CoroutineScope,
  note: GitLabNote,
  isMainNote: Flow<Boolean>
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val id: String = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = note is GitLabMergeRequestDraftNote

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note is MutableGitLabNote && note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, note) else null

  override val body: Flow<String> = note.body
  override val bodyHtml: Flow<String> = body.map { GitLabUIUtil.convertToHtml(it) }.modelFlow(cs, LOG)

  override val discussionState: Flow<GitLabDiscussionStateContainer> = isMainNote.map {
    if (it) {
      val outdated = note.asSafely<GitLabMergeRequestNote>()?.positionMapping?.map { mapping ->
        mapping is GitLabMergeRequestNotePositionMapping.Outdated || mapping is GitLabMergeRequestNotePositionMapping.Obsolete
      } ?: flowOf(false)
      GitLabDiscussionStateContainer(note.resolved, outdated)
    }
    else {
      GitLabDiscussionStateContainer.DEFAULT
    }
  }

  suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }
}