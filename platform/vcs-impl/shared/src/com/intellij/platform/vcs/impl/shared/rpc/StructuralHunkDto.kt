// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.diff.util.LineRange
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.HunkName
import com.intellij.openapi.vcs.changes.HunkNamePart
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A hunk of a modified file as a tree shows it: its lines on both sides, and its name. A side runs from its start line to the line after its
 * end, so a side without lines has an empty range.
 */
@Serializable
@ApiStatus.Internal
data class StructuralHunkDto(
  val baseStartLine: Int,
  val baseEndLine: Int,
  val currentStartLine: Int,
  val currentEndLine: Int,
  val parts: List<StructuralHunkPartDto>,
  val detail: StructuralHunkPartDto?,
  val icon: IconId?,
  val isWhitespaceOnly: Boolean,
) {
  /** The lines of the hunk on both sides, as the dim of a diff takes them. */
  val diffLines: ChangesBrowserStructuralNode.DiffLines
    get() = ChangesBrowserStructuralNode.DiffLines(LineRange(baseStartLine, baseEndLine), LineRange(currentStartLine, currentEndLine))

  companion object {
    /** The hunk with the [lines] and the [name] that the backend computed. */
    fun of(lines: HunkLines, name: HunkName): StructuralHunkDto = StructuralHunkDto(
      baseStartLine = lines.vcsLine1,
      baseEndLine = lines.vcsLine2,
      currentStartLine = lines.line1,
      currentEndLine = lines.line2,
      parts = name.parts.map(StructuralHunkPartDto::of),
      detail = name.detail?.let(StructuralHunkPartDto::of),
      icon = name.icon?.rpcIdOrNull(),
      isWhitespaceOnly = name.isWhitespaceOnly,
    )
  }
}

/** An element that a hunk changes. [previousText] is the old name of a renamed element. */
@Serializable
@ApiStatus.Internal
data class StructuralHunkPartDto(val text: @NlsSafe String, val fileStatusId: String, val previousText: @NlsSafe String? = null) {
  /** The change of the element: added, modified, or deleted. */
  val fileStatus: FileStatus
    get() = PLATFORM_FILE_STATUSES[fileStatusId] ?: FileStatus.MODIFIED

  companion object {
    fun of(part: HunkNamePart): StructuralHunkPartDto = StructuralHunkPartDto(part.text, part.status.id, part.previousText)
  }
}
