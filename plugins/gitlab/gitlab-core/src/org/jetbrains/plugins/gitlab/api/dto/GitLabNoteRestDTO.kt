// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.gitlab.api.GitLabRestId
import org.jetbrains.plugins.gitlab.api.GitLabRestIdData
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.LineRangeDTO
import java.util.*

data class GitLabNoteRestDTO(
  @JsonProperty("id")
  private val _id: String,
  val author: GitLabUserRestDTO,
  val body: String,
  val createdAt: Date,
  @SinceGitLab("10.8")
  val position: Position?,
  val resolvable: Boolean,
  @SinceGitLab("10.8")
  val resolved: Boolean,
  val system: Boolean,
) {
  @JsonIgnore
  val id: GitLabRestId = GitLabRestIdData(_id, "DiffNote")

  data class Position(
    val baseSha: String?,
    val headSha: String,
    val startSha: String,
    val positionType: String,
    val newPath: String?,
    val newLine: Int?,
    val oldPath: String?,
    val oldLine: Int?,
    @SinceGitLab("13.2")
    val lineRange: LineRangeDTO? = null,
  )
}
