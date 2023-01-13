// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.dto.*
import java.util.*

sealed interface GitLabMergeRequestTimelineItem {
  val actor: GitLabUserDTO
  val date: Date

  class Discussion(
    val discussion: GitLabDiscussionDTO
  ) : GitLabMergeRequestTimelineItem {
    override val actor: GitLabUserDTO = discussion.notes.first().author
    override val date: Date = discussion.createdAt
  }

  class StateEvent(
    val event: GitLabResourceStateEventDTO
  ) : GitLabMergeRequestTimelineItem {
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }

  class LabelEvent(
    val event: GitLabResourceLabelEventDTO
  ) : GitLabMergeRequestTimelineItem {
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }

  class MilestoneEvent(
    val event: GitLabResourceMilestoneEventDTO
  ) : GitLabMergeRequestTimelineItem {
    override val actor: GitLabUserDTO = event.user
    override val date: Date = event.createdAt
  }
}