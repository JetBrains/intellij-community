// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.withoutContext
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.DiffPathsInput

data class GitLabMergeRequestNewDiscussionPosition(
  val baseSha: String,
  val startSha: String,
  val oldLineIndex: Int?,
  val headSha: String,
  val newLineIndex: Int?,
  val paths: DiffPathsInput
) {

  companion object {
    fun calcFor(diffData: GitTextFilePatchWithHistory, location: DiffLineLocation): GitLabMergeRequestNewDiscussionPosition {
      val patch = diffData.patch
      val startSha = patch.beforeVersionId!!
      val headSha = patch.afterVersionId!!
      val baseSha = if (diffData.isCumulative) diffData.fileHistory.findStartCommit()!! else startSha

      // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/325161 we need line index for both sides for context lines
      val otherSide = patch.transferToOtherSide(location)
      val lineBefore = if (location.first == Side.LEFT) location.second else otherSide
      val lineAfter = if (location.first == Side.RIGHT) location.second else otherSide

      val pathBefore = patch.beforeName
      val pathAfter = patch.afterName

      // Due to https://gitlab.com/gitlab-org/gitlab/-/issues/296829 we need base ref here
      val positionInput = GitLabMergeRequestNewDiscussionPosition(
        baseSha,
        startSha,
        lineBefore,
        headSha,
        lineAfter,
        DiffPathsInput(pathBefore, pathAfter)
      )
      return positionInput
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
}