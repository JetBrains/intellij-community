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
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.parser.LinkMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.github.api.GithubServerPath
import java.net.URI

// TODO: fix bug with CRLF line endings from markdown library
class GHMarkdownToHtmlConverter(private val project: Project?) {
  companion object {
    const val OPEN_PR_LINK_PREFIX = "ghpullrequest:"

    private val CAPTURE_USER_ID_REGEX = """(?<=^|[\s\p{Punct}])(@[a-zA-Z0-9-]+)(?=[\s\p{Punct}]|$)""".toRegex(RegexOption.MULTILINE)
    private val CAPTURE_PR_ID_REGEX = """(?<=^|[\s\p{Punct}])(#\d+)(?=[\s\p{Punct}]|$)""".toRegex(RegexOption.MULTILINE)
  }

  /**
   * Marks links to other pull requests (recognized by a prefixed '#') as special links.
   */
  fun convertMarkdown(markdownText: @NlsSafe String, server: GithubServerPath? = null): @NlsSafe String {
    val text = markdownText.replace("\r", "")
    val flavourDescriptor = GithubFlavourDescriptor(CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, server?.toUrl())
  }

  fun convertMarkdownWithSuggestedChange(
    markdownText: @NlsSafe String,
    filePath: @NonNls String,
    reviewContent: @NonNls String,
    server: GithubServerPath? = null
  ): @NlsSafe String {
    val text = markdownText.replace("\r", "")
    val htmlSyntaxHighlighter = GHSuggestionHtmlSyntaxHighlighter(project, filePath, reviewContent)
    val flavourDescriptor = GithubFlavourDescriptor(htmlSyntaxHighlighter)

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, server?.toUrl())
  }


  private class GithubFlavourDescriptor(
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
  ) : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val map = super.createHtmlGeneratingProviders(linkMap, baseURI)
      return map + mapOf(
        GFMElementTypes.STRIKETHROUGH to SimpleInlineTagProvider("strike", 2, -2),
        MarkdownElementTypes.CODE_FENCE to CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter),
        MarkdownElementTypes.INLINE_LINK to GHLinkGeneratingProvider(map[MarkdownElementTypes.INLINE_LINK]),
        MarkdownTokenTypes.TEXT to GHTextGeneratingProvider(baseURI)
      )
    }
  }

  private class GHTextGeneratingProvider(private val baseURI: URI?) : GeneratingProvider {
    override fun processNode(
      visitor: HtmlGenerator.HtmlGeneratingVisitor,
      text: String,
      node: ASTNode,
    ) {
      if (node.getParentOfType(MarkdownElementTypes.CODE_SPAN) != null) {
        visitor.consumeHtml(text)
        return
      }
      val toString = node.getTextInNode(text).toString()
      val result = toString.preprocessPullRequestIds().preprocessUserIds()
      visitor.consumeHtml(result)
    }

    private fun String.preprocessPullRequestIds(): String {
      if (!contains('#')) return this
      return replace(CAPTURE_PR_ID_REGEX) {
        val pr = it.value
        val prId = pr.substring(1)
        val url = "$OPEN_PR_LINK_PREFIX$prId"
        HtmlChunk.link(url, pr).toString()
      }
    }

    private fun String.preprocessUserIds(): String {
      if (!contains('@')) return this
      return replace(CAPTURE_USER_ID_REGEX) {
        val mention = it.value
        val userLogin = mention.substring(1)
        val url = baseURI?.resolve(userLogin)?.toString() ?: mention
        HtmlChunk.link(url, mention).toString()
      }
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

      // Otherwise, treat the link as a normal browser-handled link
      visitor.consumeHtml(HtmlChunk.link(linkDestination, linkText).toString())
      return
    }
  }
}

@ApiStatus.Internal
fun String.convertToHtml(project: Project, server: GithubServerPath? = null): @NlsSafe String {
  val processedText = processIssueIdsMarkdown(project, this)
  return GHMarkdownToHtmlConverter(project).convertMarkdown(processedText, server)
}