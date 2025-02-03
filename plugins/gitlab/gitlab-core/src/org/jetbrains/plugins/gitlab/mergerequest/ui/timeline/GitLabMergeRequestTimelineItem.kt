// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceLabelEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceMilestoneEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabResourceStateEventDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNote
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import java.util.*

sealed interface GitLabMergeRequestTimelineItem {

  val id: String
  val date: Date?

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

  class SystemNote(
    note: GitLabNote
  ) : Immutable {
    override val id: String = note.id.toString()
    override val actor: GitLabUserDTO = note.author
    override val date: Date? = note.createdAt

    val content: String = note.body.value
  }

  class DraftNote(
    val note: GitLabMergeRequestNote
  ) : GitLabMergeRequestTimelineItem {
    override val id: String = note.id.toString()
    override val date: Date? = note.createdAt
  }

  class UserDiscussion(
    val discussion: GitLabMergeRequestDiscussion
  ) : GitLabMergeRequestTimelineItem {

    override val id: String = discussion.id.toString()
    override val date: Date = discussion.createdAt
  }
}