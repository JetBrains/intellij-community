// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import java.util.*

sealed interface GitLabMergeRequestTimelineItem {

  val id: String
  val date: Date

  sealed interface Immutable : GitLabMergeRequestTimelineItem {
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

    override val id: String = discussion.id
    override val actor: GitLabUserDTO = firstNote.author
    override val date: Date = discussion.createdAt

    val content: String = firstNote.body
  }

  class UserDiscussion(
    val discussion: GitLabDiscussion
  ) : GitLabMergeRequestTimelineItem {

    override val id: String = discussion.id
    override val date: Date = discussion.createdAt
  }
}