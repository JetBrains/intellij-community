// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.withoutContext
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInputDTO

/**
 * Builds a [GitLabMergeRequestNewDiscussionPosition] from the diff.
 *
 * It stays in the backend module, because it needs the Git diff data. The position itself is shared, because the
 * review UI needs it.
 */
object GitLabNewDiscussionPositionFactory {
  fun calcFor(diffData: GitTextFilePatchWithHistory, location: GitLabNoteLocation): GitLabMergeRequestNewDiscussionPosition {
    val patch = diffData.patch
    val startSha = patch.beforeVersionId!!
    val headSha = patch.afterVersionId!!
    val baseSha = if (diffData.isCumulative) diffData.fileHistory.findStartCommit()!! else startSha

    // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/325161 we need line index for both sides for context lines
    val (lineBefore, lineAfter) = beforeAndAfterLines(patch, location.side to location.lineIdx)
    val (startLineBefore, startLineAfter) = beforeAndAfterLines(patch, location.startSide to location.startLineIdx)

    val pathBefore = patch.beforeName
    val pathAfter = patch.afterName

    val lineRange = NewDiscussionLineRange(
      start = NewDiscussionLinePosition(
        side = location.startSide,
        oldLineIndex = startLineBefore,
        newLineIndex = startLineAfter,
      ),
      end = NewDiscussionLinePosition(
        side = location.side,
        oldLineIndex = lineBefore,
        newLineIndex = lineAfter,
      )
    )

    // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/296829 we need base ref here
    val positionInput = GitLabMergeRequestNewDiscussionPosition(
      baseSha,
      startSha,
      headSha,
      DiffPathsInputDTO(pathBefore, pathAfter),
      lineRange,
      commitSha = headSha.takeIf { !diffData.isCumulative }
    )
    return positionInput
  }

  private fun beforeAndAfterLines(patch: TextFilePatch, lineLocation: DiffLineLocation): Pair<Int?, Int?> {
    val otherSideStart = patch.transferToOtherSide(lineLocation)
    val lineBefore = if (lineLocation.first == Side.LEFT) lineLocation.second else otherSideStart
    val lineAfter = if (lineLocation.first == Side.RIGHT) lineLocation.second else otherSideStart
    return lineBefore to lineAfter
  }

  private fun TextFilePatch.transferToOtherSide(location: DiffLineLocation): Int? {
    val (side, lineIndex) = location
    var lastEndBefore = 0
    var lastEndAfter = 0
    for (hunk in hunks.withoutContext()) {
      when (side) {
        Side.LEFT -> {
          if (lineIndex < hunk.start1) {
            break
          }
          if (lineIndex in hunk.start1 until hunk.end1) {
            return null
          }
        }
        Side.RIGHT -> {
          if (lineIndex < hunk.start2) {
            break
          }
          if (lineIndex in hunk.start2 until hunk.end2) {
            return null
          }
        }
      }
      lastEndBefore = hunk.end1
      lastEndAfter = hunk.end2
    }
    return when (side) {
      Side.LEFT -> lastEndAfter + (lineIndex - lastEndBefore)
      Side.RIGHT -> lastEndBefore + (lineIndex - lastEndAfter)
    }
  }
}
