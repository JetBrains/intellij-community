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
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.ImageGeneratingProvider
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.html.makeXssSafe
import org.intellij.markdown.html.makeXssSafeDestination
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.LocalParsingResult
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.markdown.parser.sequentialparsers.impl.AutolinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser
import org.intellij.markdown.parser.sequentialparsers.impl.InlineLinkParser
import org.intellij.markdown.parser.sequentialparsers.impl.MathParser
import org.intellij.markdown.parser.sequentialparsers.impl.ReferenceLinkParser
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.net.URI


private val MARKDOWN_IMAGE_SETTINGS: IElementType = MarkdownElementType("MARKDOWN_IMAGE_SETTINGS")

private const val UPLOADS_PATH = "/uploads/"

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
    markdownSource: @NonNls String,
    uploadFileUrlBase: String
  ): @NlsSafe String {
    if (markdownSource.isBlank()) return markdownSource
    // TODO: fix bug with CRLF line endings from markdown library
    val text = preprocessMergeRequestIds(processIssueIdsMarkdown(project, markdownSource)).replace("\r", "")
    val flavourDescriptor = GitLabFlavourDescriptor(gitRepository, projectPath, CodeBlockHtmlSyntaxHighlighter(project),
                                                    uploadFileUrlBase)

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  private fun preprocessMergeRequestIds(markdownSource: String): String =
    markdownSource.replace("(!\\d+)".toRegex(), "[$1]($1)")

  private class GitLabFlavourDescriptor(
    private val gitRepository: GitRepository,
    private val projectPath: GitLabProjectPath,
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter,
    private val uploadFileUrlBase: String,
  ) : GFMFlavourDescriptor() {

    /**
     * The same as for GFMFlavourDescriptor, but with custom GitLabImageParser
     */
    override val sequentialParserManager = object : SequentialParserManager() {
      override fun getParserSequence(): List<SequentialParser> {
        return listOf(AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
                      BacktickParser(),
                      MathParser(),
                      GitLabImageParser(),
                      InlineLinkParser(),
                      ReferenceLinkParser(),
                      EmphasisLikeParser(EmphStrongDelimiterParser(), StrikeThroughDelimiterParser()))
      }
    }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val map = super.createHtmlGeneratingProviders(linkMap, baseURI)
      return map + hashMapOf(
        MarkdownElementTypes.IMAGE to GitLabImageWithSettingsGeneratingProvider(linkMap, baseURI).makeXssSafe(useSafeLinks),
        GFMElementTypes.STRIKETHROUGH to SimpleInlineTagProvider("strike", 2, -2),
        MarkdownElementTypes.CODE_FENCE to CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter),
        MarkdownElementTypes.INLINE_LINK to GitLabLinkGeneratingProvider(gitRepository, projectPath, uploadFileUrlBase, map[MarkdownElementTypes.INLINE_LINK]),
      )
    }
  }

  /**
   * Copied ImageParser with the only difference: image can contain settings block in curly braces in the end. Examples:
   * ```
   * ![image](url){width=50% height=50%}
   * ```
   * ```
   * [link]: url "label"
   * The picture ![image][link]{width=10 height=10}
   * ```
   * @see org.intellij.markdown.parser.sequentialparsers.impl.ImageParser
   */
  private class GitLabImageParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
      val result = SequentialParser.ParsingResultBuilder()
      val delegateIndices = RangesListBuilder()
      var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

      while (iterator.type != null) {
        if (iterator.type == MarkdownTokenTypes.EXCLAMATION_MARK
            && iterator.rawLookup(1) == MarkdownTokenTypes.LBRACKET) {
          val link = InlineLinkParser.parseInlineLink(iterator.advance())
                     ?: ReferenceLinkParser.parseReferenceLink(iterator.advance())

          if (link != null) {
            val index = iterator.index
            val parsedSettings = parseImageSettings(link.iteratorPosition.advance(), tokens)
            val lastIteratorPosition = parsedSettings?.iteratorPosition ?: link.iteratorPosition

            result
              .withNode(SequentialParser.Node(index..lastIteratorPosition.index + 1, MarkdownElementTypes.IMAGE))
              .withOtherParsingResult(link)

            parsedSettings?.let { result.withOtherParsingResult(it) }

            iterator = lastIteratorPosition.advance()
            continue
          }
        }
        delegateIndices.put(iterator.index)
        iterator = iterator.advance()
      }

      return result.withFurtherProcessing(delegateIndices.get())
    }

    /**
     * Parses image settings in curly braces at the end of the image link.
     * If any symbols follow the "}" without a space, they also will be a part of the parsed result
     * `![image](url){settings}other text` -> `{settings}other`
     */
    private fun parseImageSettings(iterator: TokensCache.Iterator, tokens: TokensCache): LocalParsingResult? {
      if (iterator.type == MarkdownTokenTypes.TEXT) {
        if (iterator.firstChar == '{') {
          var it = iterator
          while (it.type != MarkdownTokenTypes.EOL && it.type != null) {
            if (tokens.getRawCharAt(it.end - 1) == '}' ||
                (it.start until it.end - 1).any { tokens.getRawCharAt(it) == '}' }) {
              val range = iterator.index..it.index + 1
              return LocalParsingResult(it, listOf(SequentialParser.Node(range, MARKDOWN_IMAGE_SETTINGS)))
            }
            it = it.advance()
          }
        }
      }
      return null
    }
  }

  /**
   * Copied ImageGeneratingProvider with the only difference: image can contain a settings block in curly braces in the end,
   * and we want to render the part which goes after '}', but avoid showing the settings block.
   * `![image](http://url){width=50% height=10}other text` -> `<img src="http://url" alt="image" />other text`
   */
  private class GitLabImageWithSettingsGeneratingProvider(linkMap: LinkMap, baseURI: URI?) : ImageGeneratingProvider(linkMap, baseURI) {
    override fun renderLink(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode, info: RenderInfo) {
      super.renderLink(visitor, text, node, info)
      node.findChildOfType(MARKDOWN_IMAGE_SETTINGS)?.let { linkNode ->
        val textInNode = linkNode.getTextInNode(text)
        val closingIndex = textInNode.indexOf('}')
        if (closingIndex > 0 && closingIndex < textInNode.length - 1) {
          visitor.consumeHtml(textInNode.substring(closingIndex + 1))
        }
      }
    }
  }

  private class GitLabLinkGeneratingProvider(
    private val gitRepository: GitRepository,
    private val projectPath: GitLabProjectPath,
    private val uploadFileUrlBase: String,
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
        visitor.consumeHtml(createLink(mrUrl, "!${mrIid}").toString())
        return
      }

      // If the link looks an awful lot like a website link or project-relative link, leave it be
      val fullProjectPath = projectPath.fullPath()
      if (linkDestination.startsWith("http:") || linkDestination.startsWith("https:") ||
          linkDestination.trimStart('/').startsWith(fullProjectPath)) {
        visitor.consumeHtml(createLink(linkDestination, linkText).toString())
        return
      }

      // "uploads" files links should be updated to absolute URLs of the uploads
      if (linkDestination.startsWith(UPLOADS_PATH)) {
        visitor.consumeHtml(createLink(
          uploadFileUrlBase + linkDestination.substring(UPLOADS_PATH.length), linkText).toString())
        return
      }

      // Otherwise, the destination is a file in the current git repo, so we can make the link go to it directly
      try {
        val fileDestination = gitRepository.root.toNioPath().resolve(linkDestination.replace('\\', '/')).toString()
        val fileDescription = "$OPEN_FILE_LINK_PREFIX${fileDestination}"
        visitor.consumeHtml(createLink(fileDescription, linkText).toString())
      }
      catch (e: Exception) {
        // If some error occurred (because the file path is invalid because it's likely a link), just leave it as is
        visitor.consumeHtml(createLink(linkDestination, linkText).toString())
      }
      return
    }

    private fun createLink(linkDestination: String, @NlsSafe linkText: String): HtmlChunk.Element =
      HtmlChunk.link(makeXssSafeDestination(linkDestination).toString(), linkText)
  }
}