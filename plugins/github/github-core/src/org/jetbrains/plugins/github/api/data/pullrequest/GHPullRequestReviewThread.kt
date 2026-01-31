// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffLineRange
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.Logger
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread.Companion.LOG
import java.util.Date

@GraphQLFragment("/graphql/fragment/pullRequestReviewThread.graphql")
data class GHPullRequestReviewThread(
  override val id: String,
  val isResolved: Boolean,
  val isOutdated: Boolean,
  val path: String,
  @JsonProperty("diffSide") val side: Side,
  val line: Int?,
  val originalLine: Int?,
  @JsonProperty("startDiffSide") val startSide: Side?,
  val startLine: Int?,
  val originalStartLine: Int?,
  // To be precise: the elements of this list can be null, but should practically never be...
  @JsonProperty("comments") private val commentsNodes: GraphQLNodesDTO<GHPullRequestReviewComment>,
  val viewerCanReply: Boolean,
  val viewerCanResolve: Boolean,
  val viewerCanUnresolve: Boolean,
) : GHNode(id) {
  @JsonIgnore
  val comments: List<GHPullRequestReviewComment> = commentsNodes.nodes

  @JsonIgnore
  private val root = commentsNodes.nodes.first()

  @JsonIgnore
  val state: GHPullRequestReviewCommentState = root.state

  @JsonIgnore
  val commit: GHCommitHash? = root.commit

  @JsonIgnore
  val originalCommit: GHCommitHash? = root.originalCommit

  @JsonIgnore
  val author: GHActor? = root.author

  @JsonIgnore
  val createdAt: Date = root.createdAt

  @JsonIgnore
  val diffHunk: String = root.diffHunk

  @JsonIgnore
  val reviewId: String? = root.reviewId

  companion object {
    internal val LOG = Logger.getInstance(GHPullRequestReviewThread::class.java)
  }
}

fun GHPullRequestReviewThread.isVisible(viewOption: DiscussionsViewOption): Boolean =
  when (viewOption) {
    DiscussionsViewOption.ALL -> true
    DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
    DiscussionsViewOption.DONT_SHOW -> false
  }

private enum class StartOrEnd {
  START, END;
}

private data class LineOnCommit(
  val commitSha: String,
  val lineIndex: Int,
)

fun GHPullRequestReviewThread.mapToLeftSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.LEFT)

fun GHPullRequestReviewThread.mapToRightSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.RIGHT)

private fun GHPullRequestReviewThread.mapToSidedLine(
  diffData: GitTextFilePatchWithHistory,
  side: Side,
): Int? {
  val (fromCommit, lineIndex) = lineOnCommit(diffData, StartOrEnd.END, side) ?: return null
  return diffData.forcefullyMapLine(fromCommit, lineIndex - 1, side)
}

fun GHPullRequestReviewThread.mapToRange(
  diffData: GitTextFilePatchWithHistory,
  sideBias: Side = Side.LEFT,
): DiffLineRange? {
  val (initialEndSide, initialEndLine) = mapToLocation(diffData, StartOrEnd.END, sideBias) ?: return null

  // there is no startLine, we are done mapping
  if (startLine == null && originalStartLine == null) {
    return (initialEndSide to initialEndLine).let { it to it }
  }

  val (initialStartSide, initialStartLine) = mapToLocation(diffData, StartOrEnd.START, initialEndSide) ?: return null
  if (initialStartSide == initialEndSide && initialStartLine > initialEndLine) {
    LOG.warn("Invalid comment range lines: $startLine..$initialEndLine")
    return null
  }

  return (initialStartSide to initialStartLine) to (initialEndSide to initialEndLine)
}

private fun GHPullRequestReviewThread.mapToLocation(
  diffData: GitTextFilePatchWithHistory,
  startOrEnd: StartOrEnd,
  sideBias: Side,
): DiffLineLocation? {
  val (commit, lineIndex) = lineOnCommit(diffData, startOrEnd, sideBias) ?: return null
  return diffData.mapLine(commit, lineIndex - 1, sideBias)
}


fun GHPullRequestReviewThread.mapToInEditorRange(diffData: GitTextFilePatchWithHistory): IntRange? {
  val threadData = this

  // already on latest and there's a mapped line, then use that one
  if (
    threadData.startSide == Side.RIGHT &&
    threadData.side == Side.RIGHT &&
    diffData.patch.afterVersionId == threadData.commit?.oid &&
    threadData.line != null
  ) {
    val startLineIndex = (threadData.startLine ?: threadData.line) - 1
    val endLineIndex = threadData.line - 1

    return startLineIndex..endLineIndex
  }

  return threadData.mapToRange(diffData, sideBias = Side.RIGHT)?.let {
    val startSide = it.first.first
    val endSide = it.second.first
    if (startSide == Side.RIGHT && endSide == Side.RIGHT) return@let it.first.second..it.second.second
    else null
  }
}

/**
 * @param sideBias Indicates what side we would prefer the lines to be mapped to.
 * We choose the line and commit that are closest to the preferred side.
 */
private fun GHPullRequestReviewThread.lineOnCommit(
  diffData: GitTextFilePatchWithHistory,
  startOrEnd: StartOrEnd,
  sideBias: Side,
): LineOnCommit? {
  val (unmappedLine, unmappedOriginalLine, threadSide) = when (startOrEnd) {
    StartOrEnd.END -> Triple(line, originalLine, side)
    StartOrEnd.START -> Triple(startLine, originalStartLine, startSide ?: side)
  }

  fun mapOriginalLine() =
    toLineOnCommit(path, diffData, threadSide, originalCommit?.oid, unmappedOriginalLine)

  fun mapLine() =
    toLineOnCommit(path, diffData, threadSide, commit?.oid, unmappedLine)

  return when (sideBias) {
    Side.LEFT -> mapOriginalLine() ?: mapLine()
    Side.RIGHT -> mapLine() ?: mapOriginalLine()
  }
}

private fun toLineOnCommit(
  file: String,
  diffData: GitTextFilePatchWithHistory,
  side: Side?,
  commitSha: String?,
  lineIndex: Int?,
): LineOnCommit? {
  val side = side ?: return null
  val commitSha = commitSha ?: return null
  val lineIndex = lineIndex ?: return null

  if (!diffData.contains(commitSha, file)) return null

  return LineOnCommit(
    when (side) {
      Side.RIGHT -> commitSha
      Side.LEFT ->
        (
          if (diffData.isCumulative) diffData.fileHistory.findStartCommit()
          else diffData.patch.beforeVersionId
        ) ?: return null
    },
    lineIndex,
  )
}
