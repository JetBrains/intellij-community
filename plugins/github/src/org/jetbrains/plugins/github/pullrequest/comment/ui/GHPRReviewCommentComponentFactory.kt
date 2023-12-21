// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import javax.swing.JComponent


internal class GHPRReviewCommentComponentFactory(
  private val project: Project,
  private val htmlImageLoader: AsyncHtmlImageLoader
) {
  private val markdownConverter = GHMarkdownToHtmlConverter(project)

  fun createCommentComponent(commentBody: String, maxTextWidth: Int): JComponent {
    val htmlBody = markdownConverter.convertMarkdown(commentBody)
    return createCommentPane(htmlBody, maxTextWidth)
  }

  fun createCommentWithSuggestedChangeComponent(
    thread: GHPRReviewThreadModel,
    suggestedChange: GHSuggestedChange,
    suggestedChangeHelper: GHPRSuggestedChangeHelper,
    maxTextWidth: Int
  ): JComponent {
    val htmlBody = markdownConverter.convertMarkdownWithSuggestedChange(
      suggestedChange.commentBody,
      suggestedChange.filePath,
      suggestedChange.cutChangedContent().joinToString("\n") { it }
    )
    val content = htmlBody.removePrefix("<body>").removeSuffix("</body>")
    val commentBlocks = collectCommentBlocks(content)

    val suggestedChangeApplier = GHSuggestedChangeApplier(project, suggestedChangeHelper.repository, suggestedChange)
    val suggestedChangeComponent = GHPRReviewSuggestedChangeComponentFactory(project, thread, suggestedChangeApplier, suggestedChangeHelper)

    return VerticalListPanel().apply {
      commentBlocks.forEach { block ->
        when (block.commentType) {
          CommentType.COMMENT -> add(createCommentPane(block.content, maxTextWidth))
          CommentType.SUGGESTED_CHANGE -> add(suggestedChangeComponent.create(block.content))
        }
      }
    }
  }

  private fun createCommentPane(htmlBody: String, maxTextWidth: Int): JComponent =
    SimpleHtmlPane(customImageLoader = htmlImageLoader).apply {
      setHtmlBody(htmlBody)
    }.let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, maxWidth = maxTextWidth)
    }

  internal enum class CommentType {
    COMMENT,
    SUGGESTED_CHANGE
  }

  internal data class CommentBlock(
    val commentType: CommentType,
    val content: String
  )

  companion object {
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
}