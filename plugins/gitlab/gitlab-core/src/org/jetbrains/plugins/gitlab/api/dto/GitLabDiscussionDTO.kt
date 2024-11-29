// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.GitLabGid
import org.jetbrains.plugins.gitlab.api.GitLabGidData
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.util.*

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/discussion.graphql")
class GitLabDiscussionDTO(
  @JsonProperty("id")
  private val _id: String,
  @JsonProperty("replyId")
  @SinceGitLab("12.1")
  private val _replyId: String,
  val createdAt: Date,
  notes: NotesConnection
) {
  @JsonIgnore
  val id: GitLabGid = GitLabGidData(_id)
  @JsonIgnore
  val replyId: GitLabGid = GitLabGidData(_replyId)

  val notes: List<GitLabNoteDTO> = notes.nodes

  class NotesConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    @SinceGitLab("12.3") nodes: List<GitLabNoteDTO>
  ) :
    GraphQLConnectionDTO<GitLabNoteDTO>(pageInfo, nodes)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabDiscussionDTO) return false

    if (_id != other._id) return false
    if (_replyId != other._replyId) return false
    if (createdAt != other.createdAt) return false
    return notes == other.notes
  }

  override fun hashCode(): Int {
    var result = _id.hashCode()
    result = 31 * result + _replyId.hashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + notes.hashCode()
    return result
  }
}
