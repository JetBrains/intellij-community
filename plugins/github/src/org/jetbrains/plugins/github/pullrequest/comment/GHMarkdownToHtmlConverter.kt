// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.jetbrains.annotations.NonNls
import java.net.URI

// TODO: fix bug with CRLF line endings from markdown library
class GHMarkdownToHtmlConverter(private val project: Project?) {

  fun convertMarkdown(markdownText: @NlsSafe String): @NlsSafe String {
    val text = markdownText.replace("\r", "")
    val flavourDescriptor = GithubFlavourDescriptor(CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  fun convertMarkdownWithSuggestedChange(markdownText: @NlsSafe String,
                                         filePath: @NonNls String,
                                         reviewContent: @NonNls String): @NlsSafe String {
    val text = markdownText.replace("\r", "")
    val htmlSyntaxHighlighter = GHSuggestionHtmlSyntaxHighlighter(project, filePath, reviewContent)
    val flavourDescriptor = GithubFlavourDescriptor(htmlSyntaxHighlighter)

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  private class GithubFlavourDescriptor(
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
  ) : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val parentProviders = super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap()
      parentProviders[MarkdownElementTypes.CODE_FENCE] = CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter)

      return parentProviders
    }
  }
}

internal fun String.convertToHtml(project: Project): @NlsSafe String {
  val processedText = processIssueIdsMarkdown(project, this)
  return GHMarkdownToHtmlConverter(project).convertMarkdown(processedText)
}