// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.diff.util.Side
import com.intellij.util.io.DigestUtil
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInputDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.LinePositionDTO

fun NewDiscussionLinePosition.toLinePositionDTO(paths: DiffPathsInputDTO): LinePositionDTO {
  val oldLine = oldLineIndex?.inc()
  val newLine = newLineIndex?.inc()
  return LinePositionDTO(
    lineCode = lineCode(side, paths.newPath, paths.oldPath, newLine, oldLine),
    type = if (side == Side.RIGHT) "new" else "old",
    oldLine = oldLine,
    newLine = newLine
  )
}

private fun lineCode(side: Side, pathAfter: String?, pathBefore: String?, lineAfter: Int?, lineBefore: Int?): String? {
  val path = if (side == Side.RIGHT) pathAfter else pathBefore
  if (path == null) return null
  val hash = DigestUtil.sha1Hex(path)
  val oldLine = lineBefore ?: lineAfter
  val newLine = lineAfter ?: lineBefore
  return "${hash}_${oldLine}_${newLine}"
}
