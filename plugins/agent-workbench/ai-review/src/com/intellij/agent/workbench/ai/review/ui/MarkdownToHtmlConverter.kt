// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.html.URI
import org.intellij.markdown.parser.LinkMap

internal class MarkdownToHtmlConverter {

  companion object {
    private val CODE_BLOCK_REGEX = Regex("\\n+</code></pre>")
  }

  internal fun convertToHtml(project: Project, textWithMarkdown: @NlsSafe String): @NlsSafe String {
    if (textWithMarkdown.isBlank()) return ""

    val descriptionFlavourDescriptor = DescriptionFlavourDescriptor(CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(descriptionFlavourDescriptor).convertMarkdownToHtml(textWithMarkdown)
      .replace(CODE_BLOCK_REGEX, "</code></pre>")
  }

  private class DescriptionFlavourDescriptor(private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter) : CommonMarkFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val parentProviders = super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap()
      parentProviders[MarkdownElementTypes.EMPH] = SimpleInlineTagProvider("i", 1, -1)
      parentProviders[MarkdownElementTypes.STRONG] = SimpleInlineTagProvider("b", 2, -2)
      parentProviders[GFMElementTypes.STRIKETHROUGH] = SimpleInlineTagProvider("strike", 2, -2)
      parentProviders[MarkdownElementTypes.CODE_FENCE] = CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter)
      parentProviders[MarkdownTokenTypes.HTML_TAG] = object : GeneratingProvider {
        override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
          visitor.consumeHtml(htmlEncode(node.getTextInNode(text).toString()))
        }
      }

      return parentProviders
    }

    private fun htmlEncode(stringToEncode: String?): String {
      if (stringToEncode.isNullOrEmpty()) {
        return ""
      }

      if (stringToEncode == UIUtil.BR) {
        return stringToEncode
      }

      val result = StringBuilder(stringToEncode.length)
      for (ch in stringToEncode) {
        when (ch) {
          ';' -> result.append("&semi;")
          '&' -> result.append("&amp;")
          '<' -> result.append("&lt;")
          '>' -> result.append("&gt;")
          '"' -> result.append("&quot;")
          '\'' -> result.append("&#x27;")
          '\\' -> result.append("&bsol;")
          '/' -> result.append("&#x2F;")
          else -> result.append(ch)
        }
      }
      return result.toString()
    }
  }
}
