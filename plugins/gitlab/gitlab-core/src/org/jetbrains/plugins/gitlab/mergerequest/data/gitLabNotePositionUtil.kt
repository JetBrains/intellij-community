// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory

object GitLabNotePositionUtil {
  fun getLocation(lineIndexLeft: Int?, lineIndexRight: Int?, contextSide: Side = Side.LEFT): DiffLineLocation? = when {
    lineIndexLeft != null && lineIndexRight != null -> when (contextSide) {
      Side.LEFT -> DiffLineLocation(Side.LEFT, lineIndexLeft)
      Side.RIGHT -> DiffLineLocation(Side.RIGHT, lineIndexRight)
    }
    lineIndexLeft != null -> DiffLineLocation(Side.LEFT, lineIndexLeft)
    lineIndexRight != null -> DiffLineLocation(Side.RIGHT, lineIndexRight)
    else -> null
  }
}

fun GitLabNotePosition.mapToLocation(diffData: GitTextFilePatchWithHistory, contextSide: Side = Side.LEFT)
  : DiffLineLocation? {
  val (side, lineIndex) = getLocation(contextSide) ?: return null
  if (!diffData.contains(parentSha, filePathBefore, sha, filePathAfter)) return null
  val revision = side.select(parentSha, sha)!!
  return diffData.mapLine(revision, lineIndex, side)
}

fun GitLabMergeRequestNewDiscussionPosition.mapToLocation(diffData: GitTextFilePatchWithHistory, contextSide: Side = Side.LEFT)
  : DiffLineLocation? {
  val (side, lineIndex) = GitLabNotePositionUtil.getLocation(oldLineIndex, newLineIndex, contextSide) ?: return null
  if (!diffData.contains(baseSha, paths.oldPath, headSha, paths.newPath)) return null
  val revision = contextSide.select(baseSha, headSha)!!
  return diffData.mapLine(revision, lineIndex, side)
}

private fun GitTextFilePatchWithHistory.contains(parentSha: String, pathAtParent: String?,
                                                 sha: String, path: String?): Boolean {
  if (pathAtParent != null && contains(parentSha, pathAtParent)) return true
  if (path != null && contains(sha, path)) return true
  return false
}

