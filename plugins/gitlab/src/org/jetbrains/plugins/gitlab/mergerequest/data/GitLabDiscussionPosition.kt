// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffRefs

sealed interface GitLabDiscussionPosition {
  val diffRefs: GitLabDiffRefs
  val filePath: String

  data class Text(
    override val diffRefs: GitLabDiffRefs,
    override val filePath: String,
    val newPath: String?,
    val newLine: Int?,
    val oldPath: String?,
    val oldLine: Int?
  ) : GitLabDiscussionPosition

  data class Image(
    override val diffRefs: GitLabDiffRefs,
    override val filePath: String
  ) : GitLabDiscussionPosition
}