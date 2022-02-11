// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeInfo
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent
import javax.swing.JPanel


class GHPRReviewCommentComponentFactory(private val project: Project) {
  private val markdownConverter = GHMarkdownToHtmlConverter(project)

  fun createCommentComponent(commentBody: String): JComponent {
    val htmlBody = markdownConverter.convertMarkdown(commentBody)
    return HtmlEditorPane(htmlBody)
  }

  fun createCommentWithSuggestedChangeComponent(
    commentBody: String,
    threadId: String,
    isOutdated: Boolean,
    suggestedChangeInfo: GHSuggestedChangeInfo,
    reviewDataProvider: GHPRReviewDataProvider,
    detailsDataProvider: GHPRDetailsDataProvider
  ): JComponent {
    val htmlBody = markdownConverter.convertMarkdownWithSuggestedChange(commentBody, suggestedChangeInfo)
    val content = htmlBody.removePrefix("<body>").removeSuffix("</body>")

    val startSuggestedChange = content.indexOf(SUGGESTED_CHANGE_BEGIN_TAG)
    val endSuggestedChange = content.indexOf(SUGGESTED_CHANGE_END_TAG) + SUGGESTED_CHANGE_END_TAG.length - 1

    val textAboveSuggestedChange = content.substring(0 until startSuggestedChange)
    val suggestedChange = content.substring(startSuggestedChange..endSuggestedChange)
    val textBelowSuggestedChange = content.substring(endSuggestedChange + 1)

    val suggestedChangeApplier = GHSuggestedChangeApplier(project, commentBody, suggestedChangeInfo)
    val suggestedChangeComponent = GHPRReviewSuggestedChangeComponentFactory(project, threadId, isOutdated, suggestedChangeApplier,
                                                                             reviewDataProvider, detailsDataProvider)

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      if (textAboveSuggestedChange.isNotEmpty()) add(HtmlEditorPane(textAboveSuggestedChange))
      add(suggestedChangeComponent.create(suggestedChange))
      if (textBelowSuggestedChange.isNotEmpty()) add(HtmlEditorPane(textBelowSuggestedChange))
    }
  }

  companion object {
    private const val SUGGESTED_CHANGE_BEGIN_TAG = "<code class=\"language-suggestion\">"
    private const val SUGGESTED_CHANGE_END_TAG = "</code>"
  }
}