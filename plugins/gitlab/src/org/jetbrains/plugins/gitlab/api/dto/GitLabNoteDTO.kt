// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import java.util.*

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/note.graphql")
data class GitLabNoteDTO(
  val author: GitLabUserDTO,
  val body: String,
  val createdAt: Date,
  val id: String,
  val position: Position?,
  val resolvable: Boolean,
  @SinceGitLab("13.1") val resolved: Boolean,
  val system: Boolean,
  @SinceGitLab("13.8") val url: String,
  val userPermissions: UserPermissions
) {
  data class Position(
    @SinceGitLab("12.4") val diffRefs: GitLabDiffRefs,
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
