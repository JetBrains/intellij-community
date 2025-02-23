// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestReviewThread.graphql")
data class GHPullRequestReviewThread(override val id: String,
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
                                     val viewerCanUnresolve: Boolean)
  : GHNode(id) {
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
}

fun GHPullRequestReviewThread.isVisible(viewOption: DiscussionsViewOption): Boolean =
  when (viewOption) {
    DiscussionsViewOption.ALL -> true
    DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
    DiscussionsViewOption.DONT_SHOW -> false
  }

fun GHPullRequestReviewThread.mapToLocation(diffData: GitTextFilePatchWithHistory, sideBias: Side? = null): DiffLineLocation? {
  val threadData = this
  if (threadData.line == null && threadData.originalLine == null) return null

  return if (threadData.line != null) {
    val commitSha = threadData.commit?.oid ?: return null
    if (!diffData.contains(commitSha, threadData.path)) return null
    when (threadData.side) {
      Side.RIGHT -> {
        diffData.mapLine(commitSha, threadData.line - 1, sideBias ?: Side.RIGHT)
      }
      Side.LEFT -> {
        diffData.fileHistory.findStartCommit()?.let { baseSha ->
          diffData.mapLine(baseSha, threadData.line - 1, sideBias ?: Side.LEFT)
        }
      }
    }
  }
  else if (threadData.originalLine != null) {
    val originalCommitSha = threadData.originalCommit?.oid ?: return null
    if (!diffData.contains(originalCommitSha, threadData.path)) return null
    when (threadData.side) {
      Side.RIGHT -> {
        diffData.mapLine(originalCommitSha, threadData.originalLine - 1, sideBias ?: Side.RIGHT)
      }
      Side.LEFT -> {
        diffData.fileHistory.findFirstParent(originalCommitSha)?.let { parentSha ->
          diffData.mapLine(parentSha, threadData.originalLine - 1, sideBias ?: Side.LEFT)
        }
      }
    }
  }
  else {
    null
  }
}
