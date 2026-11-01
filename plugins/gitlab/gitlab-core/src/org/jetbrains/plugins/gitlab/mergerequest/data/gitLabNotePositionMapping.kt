// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory

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

