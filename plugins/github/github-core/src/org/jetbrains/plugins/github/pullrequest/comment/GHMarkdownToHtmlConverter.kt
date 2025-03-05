// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.parser.LinkMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.net.URI

// TODO: fix bug with CRLF line endings from markdown library
class GHMarkdownToHtmlConverter(private val project: Project?) {
  companion object {
    const val OPEN_PR_LINK_PREFIX = "ghpullrequest:"

    private val CAPTURE_PR_ID_REGEX = "(#\\d+)".toRegex()
  }

  /**
   * Marks links to other pull requests (recognized by a prefixed '#') as special links.
   */
  fun convertMarkdown(markdownText: @NlsSafe String): @NlsSafe String {
    val text = preprocessPullRequestIds(markdownText.replace("\r", ""))
    val flavourDescriptor = GithubFlavourDescriptor(CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  fun convertMarkdownWithSuggestedChange(markdownText: @NlsSafe String,
                                         filePath: @NonNls String,
                                         reviewContent: @NonNls String): @NlsSafe String {
    val text = preprocessPullRequestIds(markdownText.replace("\r", ""))
    val htmlSyntaxHighlighter = GHSuggestionHtmlSyntaxHighlighter(project, filePath, reviewContent)
    val flavourDescriptor = GithubFlavourDescriptor(htmlSyntaxHighlighter)

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  private fun preprocessPullRequestIds(markdownSource: String): String =
    markdownSource.replace(CAPTURE_PR_ID_REGEX, "[$1]($1)")

  private class GithubFlavourDescriptor(
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
  ) : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val map = super.createHtmlGeneratingProviders(linkMap, baseURI)
      return map + mapOf(
        GFMElementTypes.STRIKETHROUGH to SimpleInlineTagProvider("strike", 2, -2),
        MarkdownElementTypes.CODE_FENCE to CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter),
        MarkdownElementTypes.INLINE_LINK to GHLinkGeneratingProvider(map[MarkdownElementTypes.INLINE_LINK])
      )
    }
  }

  private class GHLinkGeneratingProvider(
    private val fallback: GeneratingProvider?
  ) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
      val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
        ?.findChildOfType(MarkdownTokenTypes.TEXT)
        ?.getTextInNode(text)
      val linkDestination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(text)

      // For now, only format complete inline links
      if (linkText == null || linkDestination == null) {
        fallback?.processNode(visitor, text, node)
        return
      }

      linkText as String
      linkDestination as String

      // If the destination starts with '#', it's either an issue ID or a PR ID
      if (linkDestination.startsWith('#')) {
        val prId = linkDestination.substring(1)
        val mrUrl = "$OPEN_PR_LINK_PREFIX$prId"
        visitor.consumeHtml(HtmlChunk.link(mrUrl, "#${prId}").toString())
        return
      }

      // Otherwise, treat the link as a normal browser-handled link
      visitor.consumeHtml(HtmlChunk.link(linkDestination, linkText).toString())
      return
    }
  }
}

@ApiStatus.Internal
fun String.convertToHtml(project: Project): @NlsSafe String {
  val processedText = processIssueIdsMarkdown(project, this)
  return GHMarkdownToHtmlConverter(project).convertMarkdown(processedText)
}