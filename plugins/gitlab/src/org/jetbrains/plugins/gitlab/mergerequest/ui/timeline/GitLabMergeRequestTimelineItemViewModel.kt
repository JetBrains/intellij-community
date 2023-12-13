// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionStateContainer
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import java.net.URL
import java.util.*

sealed interface GitLabMergeRequestTimelineItemViewModel {
  data class Immutable(val item: GitLabMergeRequestTimelineItem.Immutable) : GitLabMergeRequestTimelineItemViewModel

  class Discussion(
    project: Project,
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    discussion: GitLabMergeRequestDiscussion
  ) : GitLabMergeRequestTimelineItemViewModel,
      GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(project, parentCs, currentUser, mr, discussion) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Discussion) return false

      if (id != other.id) return false

      return true
    }

    override fun hashCode(): Int = id.hashCode()
  }

  class DraftNote(
    project: Project,
    parentCs: CoroutineScope,
    mr: GitLabMergeRequest,
    note: GitLabMergeRequestNote
  ) : GitLabMergeRequestTimelineItemViewModel, GitLabNoteViewModel {
    private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

    override val id: GitLabId = note.id
    override val author: GitLabUserDTO = note.author
    override val createdAt: Date? = note.createdAt
    override val isDraft: Boolean = note is GitLabMergeRequestDraftNote
    override val serverUrl: URL = mr.glProject.serverPath.toURL()

    override val actionsVm: GitLabNoteAdminActionsViewModel? =
      if (note is MutableGitLabNote && note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, note) else null

    override val body: Flow<String> = note.body
    override val bodyHtml: Flow<String> = body.map { GitLabUIUtil.convertToHtml(project, it) }.modelFlow(cs, LOG)

    override val discussionState: Flow<GitLabDiscussionStateContainer> = flowOf(GitLabDiscussionStateContainer.DEFAULT)

    val diffVm: Flow<GitLabDiscussionDiffViewModel?> =
      note.position.map { pos -> pos?.let { GitLabDiscussionDiffViewModelImpl(cs, mr, it) } }
        .modelFlow(cs, LOG)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is DraftNote) return false

      if (id != other.id) return false

      return true
    }

    override fun hashCode(): Int {
      return id.hashCode()
    }

    companion object {
      private val LOG = logger<DraftNote>()
    }
  }
}