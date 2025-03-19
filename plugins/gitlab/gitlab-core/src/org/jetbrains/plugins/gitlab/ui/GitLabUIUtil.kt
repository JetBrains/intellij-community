// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import git4idea.repo.GitRepository
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
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.URI

object GitLabUIUtil {
  const val OPEN_FILE_LINK_PREFIX = "glfilelink:"
  const val OPEN_MR_LINK_PREFIX = "glmergerequest:"

  /**
   * Makes file links relative to the git repository root.
   * Also parses issue IDs and merge request IDs and makes appropriate links. MR ID links are used again
   * by a custom hyperlink listener to perform appropriate actions.
   *
   * @param projectPath Path of the repository (group/subgroup/repo-name), used for recognizing site-relative links.
   *
   * @see org.jetbrains.plugins.gitlab.mergerequest.util.GitLabHtmlPaneUtilKt.addGitLabHyperlinkListener
   */
  internal fun convertToHtml(
    project: Project,
    gitRepository: GitRepository,
    projectPath: GitLabProjectPath,
    markdownSource: @NonNls String
  ): @NlsSafe String {
    if (markdownSource.isBlank()) return markdownSource
    // TODO: fix bug with CRLF line endings from markdown library
    val text = preprocessMergeRequestIds(processIssueIdsMarkdown(project, markdownSource)).replace("\r", "")
    val flavourDescriptor = GitLabFlavourDescriptor(gitRepository, projectPath, CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  private fun preprocessMergeRequestIds(markdownSource: String): String =
    markdownSource.replace("(!\\d+)".toRegex(), "[$1]($1)")

  private class GitLabFlavourDescriptor(
    private val gitRepository: GitRepository,
    private val projectPath: GitLabProjectPath,
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
  ) : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val map = super.createHtmlGeneratingProviders(linkMap, baseURI)
      return map + hashMapOf(
        GFMElementTypes.STRIKETHROUGH to SimpleInlineTagProvider("strike", 2, -2),
        MarkdownElementTypes.CODE_FENCE to CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter),
        MarkdownElementTypes.INLINE_LINK to GitLabLinkGeneratingProvider(gitRepository, projectPath, map[MarkdownElementTypes.INLINE_LINK]),
      )
    }
  }

  private class GitLabLinkGeneratingProvider(
    private val gitRepository: GitRepository,
    private val projectPath: GitLabProjectPath,
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

      // If the destination starts with '!', it's a GitLab MR reference
      if (linkDestination.startsWith('!')) {
        val mrIid = linkDestination.substring(1)
        val mrUrl = "$OPEN_MR_LINK_PREFIX$mrIid"
        visitor.consumeHtml(HtmlChunk.link(mrUrl, "!${mrIid}").toString())
        return
      }

      // If the link looks an awful lot like a website link or project-relative link, leave it be
      val fullProjectPath = projectPath.fullPath()
      if (linkDestination.startsWith("http:") || linkDestination.startsWith("https:") ||
          linkDestination.trimStart('/').startsWith(fullProjectPath)) {
        visitor.consumeHtml(HtmlChunk.link(linkDestination, linkText).toString())
        return
      }

      // Otherwise, the destination is a file in the current git repo, so we can make the link go to it directly
      try {
        val fileDestination = gitRepository.root.toNioPath().resolve(linkDestination.replace('\\', '/')).toString()
        val fileDescription = "$OPEN_FILE_LINK_PREFIX${fileDestination}"
        visitor.consumeHtml(HtmlChunk.link(fileDescription, linkText).toString())
      }
      catch (e: Exception) {
        // If some error occurred (because the file path is invalid because it's likely a link), just leave it as is
        visitor.consumeHtml(HtmlChunk.link(linkDestination, linkText).toString())
      }
      return
    }
  }
}