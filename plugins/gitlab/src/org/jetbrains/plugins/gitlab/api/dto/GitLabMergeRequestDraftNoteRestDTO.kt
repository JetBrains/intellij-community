// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.gitlab.api.GitLabRestId
import org.jetbrains.plugins.gitlab.api.GitLabRestIdData

data class GitLabMergeRequestDraftNoteRestDTO(
  @JsonProperty("id")
  private val _id: Long,
  val note: String,
  val authorId: Long,
  @JsonProperty("discussion_id")
  private val _discussionId: String?,
  val position: Position
) {
  @JsonIgnore
  val id: GitLabRestId = GitLabRestIdData(_id.toString()) // No domain, because draft notes are not yet part of the GQL API.
  @JsonIgnore
  val discussionId: GitLabRestId? = _discussionId?.let { GitLabRestIdData(it, "Discussion") }

  data class Position(
    val baseSha: String?,
    val headSha: String?,
    val startSha: String?,
    val positionType: String,
    val newPath: String?,
    val newLine: Int?,
    val oldPath: String?,
    val oldLine: Int?
  )
}
