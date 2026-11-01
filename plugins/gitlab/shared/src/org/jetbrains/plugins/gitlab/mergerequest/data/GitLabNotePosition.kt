// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.diff.util.Side

sealed interface GitLabNotePosition {
  val parentSha: String
  val sha: String
  val filePathBefore: String?
  val filePathAfter: String?

  interface WithLine : GitLabNotePosition {
    val lineIndexLeft: Int?
    val lineIndexRight: Int?
    val startLineIndexLeft: Int?
    val startLineIndexRight: Int?
    val endLineIndexLeft: Int?
    val endLineIndexRight: Int?
  }

  data class Text(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
    override val lineIndexLeft: Int?,
    override val lineIndexRight: Int?,
    override val startLineIndexLeft: Int?,
    override val startLineIndexRight: Int?,
    override val endLineIndexLeft: Int?,
    override val endLineIndexRight: Int?,
  ) : WithLine

  data class Image(
    override val parentSha: String,
    override val sha: String,
    override val filePathBefore: String?,
    override val filePathAfter: String?,
  ) : GitLabNotePosition
}

val GitLabNotePosition.filePath: String
  get() = (filePathAfter ?: filePathBefore)!!

fun GitLabNotePosition.getLocation(contextSide: Side = Side.LEFT): DiffLineRange? {
  val position = this
  if (position !is GitLabNotePosition.WithLine) return null
  return GitLabNotePositionUtil.getLocation(position, contextSide)
}