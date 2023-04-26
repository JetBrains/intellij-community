// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import java.util.*

@GraphQLFragment("/graphql/fragment/note.graphql")
data class GitLabNoteDTO(
  val author: GitLabUserDTO,
  val body: String,
  val createdAt: Date,
  val id: String,
  val position: Position?,
  val resolvable: Boolean,
  val resolved: Boolean,
  val system: Boolean,
  val url: String,
  val userPermissions: UserPermissions
) {
  data class Position(
    val diffRefs: GitLabDiffRefs,
    val filePath: String,
    val positionType: String,
    val newPath: String?,
    val newLine: Int?,
    val oldPath: String?,
    val oldLine: Int?
  )

  data class UserPermissions(
    val resolveNote: Boolean,
    val adminNote: Boolean
  )
}
