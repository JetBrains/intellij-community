// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.ComponentListPanelFactory.createVertical
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.setHtmlBody
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal object GHPRReviewCommentBodyComponentFactory {
  fun createIn(cs: CoroutineScope, vm: GHPRReviewCommentBodyViewModel, maxTextWidth: Int): JComponent =
    createVertical(cs, vm.blocks) { block ->
      when (block) {
        is GHPRCommentBodyBlock.HTML -> SimpleHtmlPane(customImageLoader = vm.htmlImageLoader).apply {
          setHtmlBody(block.body)
        }.let {
          CollaborationToolsUIUtil.wrapWithLimitedSize(it, maxWidth = maxTextWidth)
        }
        is GHPRCommentBodyBlock.SuggestedChange -> {
          GHPRReviewSuggestedChangeComponentFactory.createIn(this, vm, block)
        }
      }
    }

  internal enum class CommentType {
    COMMENT,
    SUGGESTED_CHANGE
  }

  internal data class CommentBlock(
    val commentType: CommentType,
    val content: String
  )

  private const val SUGGESTED_CHANGE_START_TAG = "<code class=\"language-suggestion\">"
  private const val SUGGESTED_CHANGE_END_TAG = "</code>"

  internal fun collectCommentBlocks(comment: String): List<CommentBlock> {
    val commentBlockRanges = collectCommentBlockRanges(comment)

    var isSuggestedChange = false
    val result = mutableListOf<CommentBlock>()
    for (range in commentBlockRanges) {
      if (range.last > range.first) {
        val commentBlock =
          if (isSuggestedChange) CommentBlock(CommentType.SUGGESTED_CHANGE, comment.substring(range.first, range.last))
          else CommentBlock(CommentType.COMMENT, comment.substring(range.first, range.last))
        result.add(commentBlock)
      }

      isSuggestedChange = !isSuggestedChange
    }

    return result
  }

  private fun collectCommentBlockRanges(comment: String): List<IntRange> {
    val indexes = mutableListOf(0)

    var startIndex = comment.indexOf(SUGGESTED_CHANGE_START_TAG)
    var endIndex = comment.indexOf(SUGGESTED_CHANGE_END_TAG) + SUGGESTED_CHANGE_END_TAG.length
    while (startIndex >= 0) {
      indexes.add(startIndex)
      indexes.add(endIndex)

      startIndex = comment.indexOf(SUGGESTED_CHANGE_START_TAG, startIndex + 1)
      endIndex = comment.indexOf(SUGGESTED_CHANGE_END_TAG, endIndex + 1) + SUGGESTED_CHANGE_END_TAG.length
    }
    indexes.add(comment.length)

    return indexes.zipWithNext { start, end -> start..end }
  }
}
