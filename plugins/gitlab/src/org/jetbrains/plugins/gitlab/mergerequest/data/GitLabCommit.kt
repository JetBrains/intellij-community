// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.commits.splitCommitMessage
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.util.*

/**
 * Model-level class representing merge-request related commits.
 */
class GitLabCommit(
  val sha: String,
  val shortId: String,
  val fullTitle: @NlsSafe String?,
  val description: @NlsSafe String?,
  val author: GitLabUserDTO?,
  val authorName: String,
  val authoredDate: Date
) {
  companion object {
    fun fromRestDTO(dto: GitLabCommitRestDTO): GitLabCommit =
      with(dto) {
        GitLabCommit(id,
                     shortId,
                     title,
                     splitCommitMessage(message).second,
                     null,
                     authorName,
                     createdAt)
      }

    fun fromGraphQLDTO(dto: GitLabCommitDTO): GitLabCommit =
      with(dto) {
        GitLabCommit(sha, shortId, fullTitle, description, author, authorName, authoredDate)
      }
  }
}