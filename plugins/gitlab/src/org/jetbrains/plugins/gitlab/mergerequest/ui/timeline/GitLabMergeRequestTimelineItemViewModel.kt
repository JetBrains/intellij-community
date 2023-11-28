// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNote

sealed interface GitLabMergeRequestTimelineItemViewModel {
  data class Immutable(val item: GitLabMergeRequestTimelineItem.Immutable) : GitLabMergeRequestTimelineItemViewModel

  class Discussion(
    project: Project,
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    discussion: GitLabMergeRequestDiscussion
  ) : GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDiscussionViewModelImpl(project, parentCs, currentUser, mr, discussion) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Discussion) return false

      if (id != other.id) return false

      return true
    }

    override fun hashCode(): Int {
      return id.hashCode()
    }
  }

  class DraftDiscussion(
    project: Project,
    parentCs: CoroutineScope,
    currentUser: GitLabUserDTO,
    mr: GitLabMergeRequest,
    note: GitLabMergeRequestNote
  ) : GitLabMergeRequestTimelineDiscussionViewModel
      by GitLabMergeRequestTimelineDraftDiscussionViewModel(project, parentCs, currentUser, mr, note) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is DraftDiscussion) return false

      if (id != other.id) return false

      return true
    }

    override fun hashCode(): Int {
      return id.hashCode()
    }
  }
}