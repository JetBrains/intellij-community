// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import java.util.*

sealed interface GitLabMergeRequestTimelineItemViewModel {

  val id: String
  val date: Date

  sealed interface Immutable : GitLabMergeRequestTimelineItemViewModel {
    val actor: GitLabUserDTO
  }

  class StateEvent(
    val event: GitLabResourceStateEventDTO
  ) : Immutable {
    override val id: String = "StateEvent:" + event.id.toString()
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }

  class LabelEvent(
    val event: GitLabResourceLabelEventDTO
  ) : Immutable {
    override val id: String = "LabelEvent:" + event.id.toString()
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }

  class MilestoneEvent(
    val event: GitLabResourceMilestoneEventDTO
  ) : Immutable {
    override val id: String = "MilestoneEvent:" + event.id.toString()
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }

  class SystemDiscussion(
    discussion: GitLabDiscussionDTO
  ) : Immutable {
    private val firstNote = discussion.notes.first()

    override val id: String = "SystemDiscussion:" + discussion.id
    override val actor: GitLabUserDTO = firstNote.author
    override val date: Date = discussion.createdAt

    val content: String = firstNote.body
  }

  class Discussion(
    parentCs: CoroutineScope,
    discussion: GitLabDiscussion
  ) : GitLabMergeRequestTimelineItemViewModel,
      GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(parentCs, discussion) {
    override val id: String = "Discussion:" + discussion.id
    override val date: Date = discussion.createdAt
  }
}