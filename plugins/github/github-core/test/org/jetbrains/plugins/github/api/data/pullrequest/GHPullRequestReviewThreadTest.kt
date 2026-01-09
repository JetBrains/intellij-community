// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHReactable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThreadTest.Util.Commit
import org.junit.jupiter.api.Test
import java.util.*

class GHPullRequestReviewThreadTest {
  @Test
  fun `mapToInEditorRange uses the current line numbers if already mapped`() {
    val thread = Util.createPRThread(
      startLine = 5, originalStartLine = 2,
      line = 6, originalLine = 3,
      commit = Commit.Last, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData()

    assertThat(thread.mapToInEditorRange(diffData))
      .isEqualTo(5..6)
  }

  @Test
  fun `mapToInEditorRange tries to map current line numbers if not mapped to last commit`() {
    val thread = Util.createPRThread(
      startLine = 5, originalStartLine = 2,
      line = 6, originalLine = 3,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, side, lineIndex ->
      if (commit == Commit.Commit2 && side == Side.RIGHT)
        side to (lineIndex + 5)
      else error("unexpected")
    })

    assertThat(thread.mapToInEditorRange(diffData))
      .isEqualTo(10..11)
  }

  @Test
  fun `mapToInEditorRange produces no range if it's mapped to the left`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 2,
      line = null, originalLine = 3,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, _, lineIndex ->
      // represents that we can only map to the left side
      if (commit == Commit.Commit1)
        Side.LEFT to (lineIndex - 1)
      else error("unexpected")
    })

    assertThat(thread.mapToInEditorRange(diffData))
      .isNull()
  }

  @Test
  fun `mapToRange can recover from startLine failing to map to the bias side`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 10,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, side, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // the endLine can be mapped to LEFT OR RIGHT
        15 if side == Side.LEFT -> Side.LEFT to (lineIndex - 1)
        15 if side == Side.RIGHT -> Side.RIGHT to (lineIndex + 1)
        // but startLine can only be mapped to the LEFT
        10 -> Side.LEFT to (lineIndex - 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.RIGHT))
      .isEqualTo((Side.LEFT to 9) to (Side.RIGHT to 16))
  }

  @Test
  fun `mapToRange can also prefer the left side`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 10,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, side, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // the endLine can be mapped to LEFT OR RIGHT
        15 if side == Side.LEFT -> Side.LEFT to (lineIndex - 1)
        15 if side == Side.RIGHT -> Side.RIGHT to (lineIndex + 1)
        // but startLine can only be mapped to the LEFT
        10 -> Side.LEFT to (lineIndex - 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.LEFT))
      .isEqualTo((Side.LEFT to 9) to (Side.LEFT to 14))
  }

  @Test
  fun `mapToRange can recover from endLine failing to map to the bias side`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 10,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, side, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // the startLine can be mapped to LEFT OR RIGHT
        10 if side == Side.LEFT -> Side.LEFT to (lineIndex - 1)
        10 if side == Side.RIGHT -> Side.RIGHT to (lineIndex + 1)
        // but endLine can only be mapped to the LEFT
        15 -> Side.LEFT to (lineIndex - 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.RIGHT))
      .isEqualTo((Side.LEFT to 9) to (Side.LEFT to 14))
  }

  @Test
  fun `mapToRange just returns endLine to endLine range if no startLine is present`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = null,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, _, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // endLine can only be mapped to the LEFT
        15 -> Side.LEFT to (lineIndex - 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.RIGHT))
      .isEqualTo((Side.LEFT to 14) to (Side.LEFT to 14))
  }

  @Test
  fun `mapToRange can become cross-sided as startLine and endLine fail to map to the same side 1`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 10,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, _, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // endLine can only be mapped to the RIGHT
        15 -> Side.RIGHT to (lineIndex + 1)
        // startLine can only be mapped to the LEFT
        10 -> Side.LEFT to (lineIndex - 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.LEFT))
      .isEqualTo((Side.LEFT to 9) to (Side.RIGHT to 16))
  }

  @Test
  fun `mapToRange can become cross-sided as startLine and endLine fail to map to the same side 2`() {
    val thread = Util.createPRThread(
      startLine = null, originalStartLine = 10,
      line = null, originalLine = 15,
      commit = Commit.Commit2, originalCommit = Commit.Commit1,
      side = Side.RIGHT,
    )

    val diffData = Util.mockDiffData(mapping = { commit, _, lineIndex ->
      if (commit != Commit.Commit1) return@mockDiffData null

      when (lineIndex) {
        // endLine can only be mapped to the LEFT
        15 -> Side.LEFT to (lineIndex - 1)
        // startLine can only be mapped to the RIGHT
        10 -> Side.RIGHT to (lineIndex + 1)
        else -> error("unexpected")
      }
    })

    assertThat(thread.mapToRange(diffData, Side.LEFT))
      .isEqualTo((Side.RIGHT to 11) to (Side.LEFT to 14))
  }

  //region: Util
  private object Util {
    enum class Commit(val sha: String) {
      // represents the commit before the PR changes
      Before("before"),

      // the first commit actually in the PR
      Commit1("commit1"),
      Commit2("commit2"),
      Commit3("commit3");

      companion object {
        // the last commit actually in the PR
        val Last: Commit = Commit3
      }
    }

    private const val PATH = "file.txt"

    fun mockDiffData(
      beforeCommit: Commit = Commit.Before,
      lastCommit: Commit = Commit.Last,

      mapping: (Commit, Side, Int) -> DiffLineLocation? = { _, _, _ -> null },
      forcefulMapping: (Commit, Side, Int) -> Int? = { _, _, _ -> null },
    ): GitTextFilePatchWithHistory = mockk<GitTextFilePatchWithHistory> {
      val mock = this

      every {
        mock.contains(any(), any())
      } answers {
        val commitSha = args[0] as String
        val file = args[1] as String

        val idx = Commit.entries.find { it.sha == commitSha }!!.ordinal
        beforeCommit.ordinal <= idx && idx <= lastCommit.ordinal && file == PATH
      }

      every {
        mock.patch.beforeVersionId
      } returns beforeCommit.sha
      every {
        mock.patch.afterVersionId
      } returns lastCommit.sha

      every {
        mock.mapLine(any(), any(), any())
      } answers {
        val fromCommit = Commit.entries.find { it.sha == args[0] as String }!!
        val lineIndex = args[1] as Int
        val side = args[2] as Side

        mapping(fromCommit, side, lineIndex)
      }

      every {
        mock.forcefullyMapLine(any(), any(), any())
      } answers {
        val fromCommit = Commit.entries.find { it.sha == args[0] as String }!!
        val lineIndex = args[1] as Int
        val side = args[2] as Side

        forcefulMapping(fromCommit, side, lineIndex)
      }
    }

    /**
     * All line numbers are 0-based for ease of reading in tests.
     */
    fun createPRThread(
      line: Int? = null,
      originalLine: Int? = null,

      startLine: Int? = null,
      originalStartLine: Int? = null,

      side: Side = Side.RIGHT,
      startSide: Side = side,

      commit: Commit? = null,
      originalCommit: Commit? = null,
    ): GHPullRequestReviewThread = GHPullRequestReviewThread(
      id = "",
      isResolved = false,
      isOutdated = false,
      path = PATH,
      side = side,
      line = line?.plus(1),
      originalLine = originalLine?.plus(1),
      startSide = startSide,
      startLine = startLine?.plus(1),
      originalStartLine = originalStartLine?.plus(1),
      commentsNodes = GraphQLNodesDTO(
        nodes = listOf(
          GHPullRequestReviewComment(
            id = "",
            url = "",
            author = null,
            body = "Some text",
            createdAt = Date(),
            reactions = GHReactable.ReactionConnection(
              GraphQLCursorPageInfoDTO("", false, "", false),
            ),
            state = GHPullRequestReviewCommentState.SUBMITTED,
            commit = commit?.sha?.let { GHCommitHash("", it, it.take(7)) },
            originalCommit = originalCommit?.sha?.let { GHCommitHash("", it, it.take(7)) },
            diffHunk = "",
            pullRequestReview = null,
            viewerCanDelete = true,
            viewerCanUpdate = true,
            viewerCanReact = true,
          )
        )),
      viewerCanReply = true,
      viewerCanResolve = true,
      viewerCanUnresolve = true,
    )
  }
  //endregion
}