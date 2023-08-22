// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabMergeRequestDraftNoteRestDTO(
  val id: Long,
  val note: String,
  val authorId: Long,
  val discussionId: String?,
  val position: Position
) {
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
