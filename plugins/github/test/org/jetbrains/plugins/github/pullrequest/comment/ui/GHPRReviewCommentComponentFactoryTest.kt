// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentComponentFactory.CommentBlock
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentComponentFactory.CommentType
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals

class GHPRReviewCommentComponentFactoryTest {
  private val simpleComment = "Some text"
  private val suggestedChangeComment = """
    <code class="language-suggestion">
      val a = 10
    </code>
  """.trimIndent()

  private val simpleCommentBlock = CommentBlock(CommentType.COMMENT, simpleComment)
  private val suggestedChangeCommentBlock = CommentBlock(CommentType.SUGGESTED_CHANGE, suggestedChangeComment)

  @Test
  fun `test only single suggested change`() {
    val expected = listOf(suggestedChangeCommentBlock)
    checkCommentBlocks(expected, suggestedChangeComment)
  }

  @Test
  fun `test single suggested change with text above`() {
    val comment = "$simpleComment$suggestedChangeComment"
    val expected = listOf(
      simpleCommentBlock,
      suggestedChangeCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test single suggested change with text below`() {
    val comment = "$suggestedChangeComment$simpleComment"
    val expected = listOf(
      suggestedChangeCommentBlock,
      simpleCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test single suggested change between comments`() {
    val comment = "$simpleComment$suggestedChangeComment$simpleComment"
    val expected = listOf(
      simpleCommentBlock,
      suggestedChangeCommentBlock,
      simpleCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test single comment between suggested changes`() {
    val comment = "$suggestedChangeComment$simpleComment$suggestedChangeComment"
    val expected = listOf(
      suggestedChangeCommentBlock,
      simpleCommentBlock,
      suggestedChangeCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test comment with two suggested changes`() {
    val comment = "$simpleComment$suggestedChangeComment$suggestedChangeComment"
    val expected = listOf(
      simpleCommentBlock,
      suggestedChangeCommentBlock,
      suggestedChangeCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test two suggested changes with comment`() {
    val comment = "$suggestedChangeComment$suggestedChangeComment$simpleComment"
    val expected = listOf(
      suggestedChangeCommentBlock,
      suggestedChangeCommentBlock,
      simpleCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test single suggested change with multiple texts above`() {
    val comment = "$simpleComment$simpleComment$simpleComment$suggestedChangeComment"
    val expected = listOf(
      CommentBlock(CommentType.COMMENT, "$simpleComment$simpleComment$simpleComment"),
      suggestedChangeCommentBlock
    )

    checkCommentBlocks(expected, comment)
  }

  @Test
  fun `test single suggested change with multiple texts below`() {
    val comment = "$suggestedChangeComment$simpleComment$simpleComment$simpleComment"
    val expected = listOf(
      suggestedChangeCommentBlock,
      CommentBlock(CommentType.COMMENT, "$simpleComment$simpleComment$simpleComment"),
    )

    checkCommentBlocks(expected, comment)
  }

  private fun checkCommentBlocks(expected: List<CommentBlock>, testedComment: String) {
    assertArrayEquals(expected.toTypedArray(), GHPRReviewCommentComponentFactory.collectCommentBlocks(testedComment).toTypedArray())
  }
}