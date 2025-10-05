// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO
import java.util.*

data class GitLabMergeRequestDetails(
  val iid: String,
  val title: @NlsSafe String,
  val createdAt: Date,
  val author: GitLabUserDTO,
  val mergeStatus: GitLabMergeStatus,
  val isMergeable: Boolean,
  val state: GitLabMergeRequestState,
  val draft: Boolean,
  val assignees: List<GitLabUserDTO>,
  val reviewers: List<GitLabUserDTO>,
  val webUrl: @NlsSafe String,
  val labels: List<String>,
  val userNotesCount: Int?,
) {
  companion object {
    fun fromRestDTO(dto: GitLabMergeRequestShortRestDTO): GitLabMergeRequestDetails =
      GitLabMergeRequestDetails(
        dto.iid,
        dto.title,
        dto.createdAt,
        GitLabUserDTO.fromRestDTO(dto.author),
        dto.mergeStatusEnum,
        dto.mergeable,
        dto.stateEnum,
        dto.draft,
        dto.assignees.map(GitLabUserDTO::fromRestDTO),
        dto.reviewers.map(GitLabUserDTO::fromRestDTO),
        dto.webUrl,
        dto.labels,
        dto.userNotesCount,
      )
  }
}