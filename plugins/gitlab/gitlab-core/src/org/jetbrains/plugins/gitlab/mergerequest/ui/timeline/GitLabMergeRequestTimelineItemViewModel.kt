// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapStateInNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionStateContainer
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteAdminActionsViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter
import java.net.URL
import java.util.*

sealed interface GitLabMergeRequestTimelineItemViewModel {
  sealed class Immutable(
    private val model: GitLabMergeRequestTimelineItem.Immutable,
    private val htmlConverter: GitLabMarkdownToHtmlConverter
  ) : GitLabMergeRequestTimelineItemViewModel {
    val date = model.date
    val actor = model.actor

    val contentHtml: String? by lazy {
      (model as? GitLabMergeRequestTimelineItem.SystemNote)?.content?.let {
        htmlConverter.convertToHtml(it)
      }
    }

    companion object {
      fun fromModel(
        model: GitLabMergeRequestTimelineItem.Immutable,
        htmlConverter: GitLabMarkdownToHtmlConverter,
      ): Immutable {

        return when (model) {
          is GitLabMergeRequestTimelineItem.StateEvent -> StateEvent(model, htmlConverter)
          is GitLabMergeRequestTimelineItem.LabelEvent -> LabelEvent(model, htmlConverter)
          is GitLabMergeRequestTimelineItem.MilestoneEvent -> MilestoneEvent(model, htmlConverter)
          is GitLabMergeRequestTimelineItem.SystemNote -> SystemNote(model, htmlConverter)
        }
      }
    }
  }

  class StateEvent(
    model: GitLabMergeRequestTimelineItem.StateEvent,
    htmlConverter: GitLabMarkdownToHtmlConverter,
  ) : Immutable(model, htmlConverter) {
    val event = model.event
  }

  class LabelEvent(
    model: GitLabMergeRequestTimelineItem.LabelEvent,
    htmlConverter: GitLabMarkdownToHtmlConverter,
  ) : Immutable(model, htmlConverter) {
    val event = model.event
  }

  class MilestoneEvent(
    model: GitLabMergeRequestTimelineItem.MilestoneEvent,
    htmlConverter: GitLabMarkdownToHtmlConverter,
  ) : Immutable(model, htmlConverter) {
    val event = model.event
  }

  class SystemNote(
    model: GitLabMergeRequestTimelineItem.SystemNote,
    htmlConverter: GitLabMarkdownToHtmlConverter,
  ) : Immutable(model, htmlConverter) {
    val content = model.content
  }

  class Discussion(
    project: Project,
    parentCs: CoroutineScope,
    projectData: GitLabProject,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    discussion: GitLabMergeRequestDiscussion,
    htmlConverter: GitLabMarkdownToHtmlConverter
  ) : GitLabMergeRequestTimelineItemViewModel,
      GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(project, parentCs, projectData, currentUser, mr, discussion, htmlConverter) {
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
    projectData: GitLabProject,
    note: GitLabMergeRequestNote,
    htmlConverter: GitLabMarkdownToHtmlConverter
  ) : GitLabMergeRequestTimelineItemViewModel, GitLabNoteViewModel {
    private val cs = parentCs.childScope(this::class)

    override val id: GitLabId = note.id
    override val author: GitLabUserDTO = note.author
    override val createdAt: Date? = note.createdAt
    override val isDraft: Boolean = note is GitLabMergeRequestDraftNote
    override val serverUrl: URL = mr.glProject.serverPath.toURL()

    override val actionsVm: GitLabNoteAdminActionsViewModel? =
      if (note is MutableGitLabNote && note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, projectData, note) else null
    override val reactionsVm: GitLabReactionsViewModel? = null

    override val body: StateFlow<String> = note.body
    override val bodyHtml: StateFlow<String> = body.mapStateInNow(cs) {
      htmlConverter.convertToHtml(it)
    }

    override val discussionState: StateFlow<GitLabDiscussionStateContainer> =
      MutableStateFlow(GitLabDiscussionStateContainer.DEFAULT)

    val diffVm: Flow<GitLabDiscussionDiffViewModel?> =
      note.position.map { pos -> pos?.let { GitLabDiscussionDiffViewModelImpl(cs, mr, it) } }
        .modelFlow(cs, LOG)

    private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()

    override fun requestFocus() {
      _focusRequestsChannel.trySend(Unit)
    }

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