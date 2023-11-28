// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.net.URL
import java.util.*

interface GitLabNoteViewModel {
  val id: GitLabId
  val author: GitLabUserDTO
  val createdAt: Date?
  val isDraft: Boolean
  val serverUrl: URL

  val discussionState: Flow<GitLabDiscussionStateContainer>

  val actionsVm: GitLabNoteAdminActionsViewModel?

  val body: Flow<@Nls String>
  val bodyHtml: Flow<@Nls String>
}

private val LOG = logger<GitLabNoteViewModel>()

class GitLabNoteViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  note: GitLabNote,
  isMainNote: Flow<Boolean>,
  glProject: GitLabProjectCoordinates
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(Dispatchers.Default)

  override val id: GitLabId = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = note is GitLabMergeRequestDraftNote
  override val serverUrl: URL = glProject.serverPath.toURL()

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note is MutableGitLabNote && note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, note) else null

  override val body: Flow<String> = note.body
  override val bodyHtml: Flow<String> = body.map { GitLabUIUtil.convertToHtml(project, it) }.modelFlow(cs, LOG)

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabNoteViewModelImpl) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}