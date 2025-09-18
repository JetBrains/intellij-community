// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.Logger
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread.Companion.LOG
import java.util.*

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

fun GHPullRequestReviewThread.mapToLeftSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.LEFT)

fun GHPullRequestReviewThread.mapToRightSideLine(diffData: GitTextFilePatchWithHistory): Int? =
  mapToSidedLine(diffData, Side.RIGHT)

private fun GHPullRequestReviewThread.mapToSidedLine(diffData: GitTextFilePatchWithHistory, side: Side): Int? {
  val threadData = this
  if (threadData.line == null && threadData.originalLine == null) return null

  val lineIndex = threadData.line ?: threadData.originalLine ?: return null
  val fromCommitSha = fromCommitSha(diffData) ?: return null

  return diffData.forcefullyMapLine(fromCommitSha, lineIndex - 1, side)
}

// TODO: Write tests to illustrate and check the working of location mapping :'(
fun GHPullRequestReviewThread.mapToLocation(diffData: GitTextFilePatchWithHistory, sideBias: Side? = null): DiffLineLocation? {
  val threadData = this
  if (threadData.line == null && threadData.originalLine == null) return null

  val lineIndex = threadData.line ?: threadData.originalLine ?: return null
  val fromCommitSha = fromCommitSha(diffData) ?: return null

  val sideBias = sideBias ?: threadData.side

  return diffData.mapLine(fromCommitSha, lineIndex - 1, sideBias)
}

fun GHPullRequestReviewThread.mapToRange(diffData: GitTextFilePatchWithHistory): Pair<Side, IntRange>? {
  val threadData = this
  val fromSha = fromCommitSha(diffData) ?: return null

  val (side, endLine) = mapToLocation(diffData) ?: return null

  val unmappedStartLine = threadData.startLine ?: threadData.originalStartLine ?: return side to endLine..endLine
  val startLine = diffData.forcefullyMapLine(fromSha, unmappedStartLine - 1, side) ?: return null

  return side to if (startLine <= endLine) {
    startLine..endLine
  }
  else {
    LOG.warn("Invalid comment range lines: $startLine..$endLine")
    endLine..startLine
  }
}


fun GHPullRequestReviewThread.mapToInEditorRange(diffData: GitTextFilePatchWithHistory): Pair<Side, IntRange>? {
  val threadData = this
  val fromCommitSha = fromCommitSha(diffData) ?: return null
  val endLine = threadData.mapToRightSideLine(diffData) ?: return null
  val side = Side.RIGHT
  val unmappedStartLine = threadData.startLine ?: threadData.originalStartLine ?: return side to endLine..endLine
  val startLine = diffData.forcefullyMapLine(fromCommitSha, unmappedStartLine - 1, side) ?: return side to endLine..endLine
  return side to if (startLine <= endLine) {
    startLine..endLine
  }
  else {
    LOG.warn("Invalid comment range lines: $startLine..$endLine")
    endLine..startLine
  }
}

private fun GHPullRequestReviewThread.fromCommitSha(diffData: GitTextFilePatchWithHistory): String? {
  val threadData = this

  return if (threadData.line != null) when (threadData.side) {
    Side.RIGHT -> threadData.commit?.oid
    Side.LEFT -> diffData.fileHistory.findStartCommit()
  }
  else if (threadData.originalLine != null) {
    val originalCommitSha = threadData.originalCommit?.oid ?: return null
    when (threadData.side) {
      Side.RIGHT -> originalCommitSha
      Side.LEFT -> diffData.fileHistory.findFirstParent(originalCommitSha)
    }
  }
  else null
}
