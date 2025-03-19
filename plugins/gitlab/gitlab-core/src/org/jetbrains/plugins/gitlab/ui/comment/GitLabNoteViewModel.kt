// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.mapStateInNow
import com.intellij.collaboration.async.stateInNow
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModelImpl
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.net.URL
import java.util.*

interface GitLabNoteViewModel {
  val id: GitLabId
  val author: GitLabUserDTO
  val createdAt: Date?
  val isDraft: Boolean
  val serverUrl: URL

  val discussionState: StateFlow<GitLabDiscussionStateContainer>

  val actionsVm: GitLabNoteAdminActionsViewModel?
  val reactionsVm: GitLabReactionsViewModel?

  val body: StateFlow<@Nls String>
  val bodyHtml: StateFlow<@Nls String>
}

class GitLabNoteViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  note: GitLabNote,
  isMainNote: Flow<Boolean>,
  currentUser: GitLabUserDTO
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(javaClass.name)

  override val id: GitLabId = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = note is GitLabMergeRequestDraftNote
  override val serverUrl: URL = projectData.projectMapping.repository.serverPath.toURL()

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note is MutableGitLabNote && note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, note) else null
  override val reactionsVm: GitLabReactionsViewModel? =
    if (note is GitLabMergeRequestNote && note.canReact) GitLabReactionsViewModelImpl(cs, projectData, note, currentUser) else null

  override val body: StateFlow<String> = note.body
  override val bodyHtml: StateFlow<String> = body.mapStateInNow(cs) {
    GitLabUIUtil.convertToHtml(project, projectData.projectMapping.gitRepository, projectData.projectMapping.repository.projectPath,it)
  }

  override val discussionState: StateFlow<GitLabDiscussionStateContainer> = isMainNote.map {
    if (it) {
      val outdated = note.asSafely<GitLabMergeRequestNote>()?.positionMapping?.map { mapping ->
        mapping is GitLabMergeRequestNotePositionMapping.Outdated || mapping is GitLabMergeRequestNotePositionMapping.Obsolete
      } ?: flowOf(false)
      GitLabDiscussionStateContainer(note.resolved, outdated)
    }
    else {
      GitLabDiscussionStateContainer.DEFAULT
    }
  }.stateInNow(cs, GitLabDiscussionStateContainer.DEFAULT)

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