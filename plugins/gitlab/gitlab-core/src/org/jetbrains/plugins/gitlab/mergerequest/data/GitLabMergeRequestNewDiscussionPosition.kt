// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.diff.util.Side
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInputDTO

data class GitLabMergeRequestNewDiscussionPosition(
  val baseSha: String,
  val startSha: String,
  val headSha: String,
  val paths: DiffPathsInputDTO,
  val lineRange: NewDiscussionLineRange,
  /**
   * SHA of the commit when the position is in a single commit diff, `null` for the cumulative diff
   */
  val commitSha: String? = null,
) : GitLabNotePosition.WithLine {
  override val parentSha: String get() = baseSha
  override val sha: String get() = headSha
  override val filePathBefore: String? get() = paths.oldPath
  override val filePathAfter: String? get() = paths.newPath
  override val lineIndexLeft: Int? get() = lineRange.end.oldLineIndex
  override val lineIndexRight: Int? get() = lineRange.end.newLineIndex
  override val startLineIndexLeft: Int? get() = lineRange.start.oldLineIndex
  override val startLineIndexRight: Int? get() = lineRange.start.newLineIndex
  override val endLineIndexLeft: Int? get() = lineRange.end.oldLineIndex
  override val endLineIndexRight: Int? get() = lineRange.end.newLineIndex
}

/**
 * Line position data with 0-based line indexes
 */
data class NewDiscussionLinePosition(
  val side: Side,
  val oldLineIndex: Int?,
  val newLineIndex: Int?,
)

data class NewDiscussionLineRange(
  val start: NewDiscussionLinePosition,
  val end: NewDiscussionLinePosition,
)