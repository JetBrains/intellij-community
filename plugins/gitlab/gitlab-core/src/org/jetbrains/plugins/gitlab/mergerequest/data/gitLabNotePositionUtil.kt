// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory

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

fun GitLabNotePosition.mapToLeftSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.LEFT)

fun GitLabNotePosition.mapToRightSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.RIGHT)

private fun GitLabNotePosition.mapToSidedLine(diffData: GitTextFilePatchWithHistory, side: Side): Int? {
  val (_, endLineLocation) = getLocation(side) ?: getLocation(side.other()) ?: return null
  if (!diffData.contains(parentSha, filePathBefore, sha, filePathAfter)) return null
  val revision = endLineLocation.first.select(parentSha, sha)
  return diffData.forcefullyMapLine(revision, endLineLocation.second, side)
}

fun GitLabNotePosition.mapToLocation(diffData: GitTextFilePatchWithHistory, contextSide: Side = Side.LEFT)
  : GitLabNoteLocation? {
  val unmappedLocation = getLocation(contextSide) ?: return null
  if (!diffData.contains(parentSha, filePathBefore, sha, filePathAfter)) return null
  return unmappedLocation.toMapped(diffData, parentSha, sha)
}

fun GitLabMergeRequestNewDiscussionPosition.mapToLocation(diffData: GitTextFilePatchWithHistory, contextSide: Side = Side.LEFT)
  : GitLabNoteLocation? {
  val unmappedLocation = getLocation(contextSide) ?: return null
  if (!diffData.contains(baseSha, paths.oldPath, headSha, paths.newPath)) return null
  return unmappedLocation.toMapped(diffData, baseSha, sha)
}

private fun DiffLineRange.toMapped(diffData: GitTextFilePatchWithHistory, parentSha: String, sha: String): GitLabNoteLocation? {
  val (startLineLocation, endLineLocation) = this
  val startRevision = startLineLocation.first.select(parentSha, sha)
  val endRevision = endLineLocation.first.select(parentSha, sha)
  val mappedStartLine = diffData.mapLine(startRevision, startLineLocation.second, startLineLocation.first)
  val mappedEndLine = diffData.mapLine(endRevision, endLineLocation.second, endLineLocation.first)
  return if (mappedStartLine != null && mappedEndLine != null)
    GitLabNoteLocation(mappedStartLine.first, mappedStartLine.second, mappedEndLine.first, mappedEndLine.second)
  else
    mappedEndLine?.let { GitLabNoteLocation(it.first, it.second, it.first, it.second) }
}

private fun GitTextFilePatchWithHistory.contains(parentSha: String, pathAtParent: String?,
                                                 sha: String, path: String?): Boolean {
  if (pathAtParent != null && contains(parentSha, pathAtParent)) return true
  if (path != null && contains(sha, path)) return true
  return false
}

