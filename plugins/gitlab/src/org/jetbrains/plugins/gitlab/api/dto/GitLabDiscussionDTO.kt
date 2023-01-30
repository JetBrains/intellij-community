// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import java.util.*

@GraphQLFragment("/graphql/fragment/discussion.graphql")
class GitLabDiscussionDTO(
  val id: String,
  val replyId: String,
  val createdAt: Date,
  notes: NotesConnection
) {
  val notes: List<GitLabNoteDTO> = notes.nodes

  class NotesConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabNoteDTO>) :
    GraphQLConnectionDTO<GitLabNoteDTO>(pageInfo, nodes)
}
