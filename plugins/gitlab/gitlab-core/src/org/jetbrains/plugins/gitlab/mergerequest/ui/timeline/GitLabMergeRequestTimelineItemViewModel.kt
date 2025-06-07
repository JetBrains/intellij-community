// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapStateInNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionStateContainer
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import java.net.URL
import java.util.*

sealed interface GitLabMergeRequestTimelineItemViewModel {
  sealed class Immutable(
    private val project: Project,
    private val mr: GitLabMergeRequest,
    private val model: GitLabMergeRequestTimelineItem.Immutable
  ) : GitLabMergeRequestTimelineItemViewModel {
    val date = model.date
    val actor = model.actor

    val contentHtml: String? by lazy {
      (model as? GitLabMergeRequestTimelineItem.SystemNote)?.content?.let {
        GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
      }
    }

    companion object {
      fun fromModel(
        project: Project,
        mr: GitLabMergeRequest,
        model: GitLabMergeRequestTimelineItem.Immutable
      ): Immutable = when (model) {
        is GitLabMergeRequestTimelineItem.StateEvent -> StateEvent(project, mr, model)
        is GitLabMergeRequestTimelineItem.LabelEvent -> LabelEvent(project, mr, model)
        is GitLabMergeRequestTimelineItem.MilestoneEvent -> MilestoneEvent(project, mr, model)
        is GitLabMergeRequestTimelineItem.SystemNote -> SystemNote(project, mr, model)
      }
    }
  }

  class StateEvent(project: Project, mr: GitLabMergeRequest, model: GitLabMergeRequestTimelineItem.StateEvent)
    : Immutable(project, mr, model) {
    val event = model.event
  }

  class LabelEvent(project: Project, mr: GitLabMergeRequest, model: GitLabMergeRequestTimelineItem.LabelEvent)
    : Immutable(project, mr, model) {
    val event = model.event
  }

  class MilestoneEvent(project: Project, mr: GitLabMergeRequest, model: GitLabMergeRequestTimelineItem.MilestoneEvent)
    : Immutable(project, mr, model) {
    val event = model.event
  }

  class SystemNote(project: Project, mr: GitLabMergeRequest, model: GitLabMergeRequestTimelineItem.SystemNote)
    : Immutable(project, mr, model) {
    val content = model.content
  }

  class Discussion(
    project: Project,
    parentCs: CoroutineScope,
    projectData: GitLabProject,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    discussion: GitLabMergeRequestDiscussion
  ) : GitLabMergeRequestTimelineItemViewModel,
      GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(project, parentCs, projectData, currentUser, mr, discussion) {
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
    override val reactionsVm: GitLabReactionsViewModel? = null

    override val body: StateFlow<String> = note.body
    override val bodyHtml: StateFlow<String> = body.mapStateInNow(cs) {
      GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
    }

    override val discussionState: StateFlow<GitLabDiscussionStateContainer> =
      MutableStateFlow(GitLabDiscussionStateContainer.DEFAULT)

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