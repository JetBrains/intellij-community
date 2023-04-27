// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import java.util.*

open class GitLabMergeRequestDetails(
  override val iid: String,
  open val title: @NlsSafe String,
  open val createdAt: Date,
  open val author: GitLabUserDTO,
  open val mergeStatus: GitLabMergeStatus,
  open val state: GitLabMergeRequestState,
  open val draft: Boolean,
  open val assignees: List<GitLabUserDTO>,
  open val reviewers: List<GitLabUserDTO>,
  open val webUrl: @NlsSafe String,
  val labels: List<String>
) : GitLabMergeRequestId {

  companion object {
    fun fromRestDTO(dto: GitLabMergeRequestShortRestDTO): GitLabMergeRequestDetails =
      GitLabMergeRequestDetails(
        dto.iid,
        dto.title,
        dto.createdAt,
        dto.author,
        dto.mergeStatusEnum,
        dto.stateEnum,
        dto.draft,
        dto.assignees,
        dto.reviewers,
        dto.webUrl,
        dto.labels
      )
  }
}