// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.util.*

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/discussion.graphql")
class GitLabDiscussionDTO(
  val id: String,
  @SinceGitLab("12.1") val replyId: String,
  val createdAt: Date,
  notes: NotesConnection
) {
  val notes: List<GitLabNoteDTO> = notes.nodes

  class NotesConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    @SinceGitLab("12.3") nodes: List<GitLabNoteDTO>
  ) :
    GraphQLConnectionDTO<GitLabNoteDTO>(pageInfo, nodes)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabDiscussionDTO) return false

    if (id != other.id) return false
    if (replyId != other.replyId) return false
    if (createdAt != other.createdAt) return false
    return notes == other.notes
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + replyId.hashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + notes.hashCode()
    return result
  }
}
