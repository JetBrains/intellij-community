// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.diff.util.Side

object GitLabNotePositionUtil {
  fun getLocation(position: GitLabNotePosition.WithLine, contextSide: Side = Side.LEFT): DiffLineRange? {
    val forceRightSide = (position.lineIndexLeft == null && position.lineIndexRight != null) ||
                         (position.startLineIndexLeft == null && position.startLineIndexRight != null)

    return when {
      forceRightSide -> getRightSideLocation(position)
      position.lineIndexLeft != null && position.lineIndexRight != null -> when (contextSide) {
        Side.LEFT -> getLeftSideLocation(position)

        Side.RIGHT -> getRightSideLocation(position)
      }
      position.lineIndexLeft != null -> getLeftSideLocation(position)
      position.lineIndexRight != null -> getRightSideLocation(position)
      else -> null
    }
  }
  private fun getLeftSideLocation(position: GitLabNotePosition.WithLine): DiffLineRange? {
    val (startSide, startLine) = position.startLineIndexLeft?.let { Side.LEFT to it } ?: (Side.RIGHT to position.startLineIndexRight)
    val (endSide, endLine) = position.endLineIndexLeft?.let { Side.LEFT to it } ?: (Side.RIGHT to position.endLineIndexRight)

    return if (startLine == null || endLine == null || // fallback to a single line
               (endSide == Side.RIGHT && endLine != position.lineIndexRight) ||
               (endSide == Side.LEFT && endLine != position.lineIndexLeft)) {
      getSingleLineLocation(position.lineIndexLeft, position.lineIndexRight, Side.LEFT)
        ?.let { single -> DiffLineRange(single, single) }
    }
    else {
        val startLoc = DiffLineLocation(startSide, startLine)
        val endLoc = DiffLineLocation(endSide, endLine)
      DiffLineRange(startLoc, endLoc)
    }
  }

  private fun getRightSideLocation(position: GitLabNotePosition.WithLine): DiffLineRange? {
    val (startSide, startLine) = position.startLineIndexRight?.let { Side.RIGHT to it } ?: (Side.LEFT to position.startLineIndexLeft)
    val (endSide, endLine) = position.endLineIndexRight?.let { Side.RIGHT to it } ?: (Side.LEFT to position.endLineIndexLeft)

    return if (startLine == null || endLine == null || // fallback to a single line
               (endSide == Side.RIGHT && endLine != position.lineIndexRight) ||
               (endSide == Side.LEFT && endLine != position.lineIndexLeft)) {
      getSingleLineLocation(position.lineIndexLeft, position.lineIndexRight, Side.RIGHT)
        ?.let { single -> DiffLineRange(single, single) }
    }
    else {
      val startLoc = DiffLineLocation(startSide, startLine)
      val endLoc = DiffLineLocation(endSide, endLine)
      DiffLineRange(startLoc, endLoc)
    }
  }

  private fun getSingleLineLocation(lineIndexLeft: Int?, lineIndexRight: Int?, contextSide: Side = Side.LEFT): DiffLineLocation? = when {
    lineIndexLeft != null && lineIndexRight != null -> when (contextSide) {
      Side.LEFT -> DiffLineLocation(Side.LEFT, lineIndexLeft)
      Side.RIGHT -> DiffLineLocation(Side.RIGHT, lineIndexRight)
    }
    lineIndexLeft != null -> DiffLineLocation(Side.LEFT, lineIndexLeft)
    lineIndexRight != null -> DiffLineLocation(Side.RIGHT, lineIndexRight)
    else -> null
  }
}
