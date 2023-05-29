// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNote

sealed interface GitLabMergeRequestTimelineItemViewModel {
  val id: String

  class Immutable(
    val item: GitLabMergeRequestTimelineItem.Immutable
  ) : GitLabMergeRequestTimelineItemViewModel {
    override val id: String = item.id
  }

  class Discussion(
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    discussion: GitLabMergeRequestDiscussion
  ) : GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(parentCs, currentUser, mr, discussion) {
    override val id: String = discussion.id
  }

  class DraftDiscussion(
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    note: GitLabMergeRequestNote
  ) : GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDraftDiscussionViewModel(parentCs, currentUser, mr, note) {
    override val id: String = note.id
  }
}