// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import java.util.*

data class GitLabMergeRequestShortDTO(
  val id: Long,
  val iid: String,
  val projectId: Long,
  val title: String,
  val description: String,
  val state: String,
  val mergeStatus: String,
  val mergeable: Boolean,
  val author: GitLabUserDTO,
  val assignees: List<GitLabUserDTO>,
  val reviewers: List<GitLabUserDTO>,
  val labels: List<String>,
  val createdAt: Date,
  val draft: Boolean,
  val webUrl: String
) {
  val stateEnum: GitLabMergeRequestState = parseState(state)

  val mergeStatusEnum: GitLabMergeStatus = parseMergeStatus(mergeStatus)

  companion object {
    private val logger: Logger = logger<GitLabMemberDTO>()

    private fun parseState(state: String): GitLabMergeRequestState = try {
      GitLabMergeRequestState.valueOf(state.uppercase(Locale.getDefault()))
    }
    catch (_: IllegalArgumentException) {
      logger.warn("Unable to parse merge request state: $state")
      GitLabMergeRequestState.ALL
    }

    private fun parseMergeStatus(mergeStatus: String): GitLabMergeStatus = try {
      GitLabMergeStatus.valueOf(mergeStatus.uppercase(Locale.getDefault()))
    }
    catch (_: IllegalArgumentException) {
      logger.warn("Unable to parse merge status: $mergeStatus")
      GitLabMergeStatus.UNCHECKED
    }
  }
}