// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation

sealed interface GitLabDiscussionPosition {
  val parentSha: String
  val sha: String
  val filePathBefore: String?
  val filePathAfter: String?

  data class Text(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
    val location: DiffLineLocation
  ) : GitLabDiscussionPosition

  data class Image(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
  ) : GitLabDiscussionPosition
}

val GitLabDiscussionPosition.filePath: String
  get() = (filePathAfter ?: filePathBefore)!!