// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import java.net.URI

internal object AgentAcpThreadMessageMarkdownRenderer {
  private val flavour = AgentAcpThreadMessageMarkdownFlavourDescriptor()

  fun renderHtmlBody(markdownText: @NlsSafe String): @NlsSafe String {
    if (markdownText.isBlank()) return ""

    val normalizedText = markdownText.replace("\r", "")
    val parsedTree = MarkdownParser(flavour, assertionsEnabled = false).buildMarkdownTreeFromString(normalizedText)
    val providers = flavour.createHtmlGeneratingProviders(
      linkMap = LinkMap.buildLinkMap(parsedTree, normalizedText),
      baseURI = null,
    )
    return HtmlGenerator(normalizedText, parsedTree, providers, includeSrcPositions = false).generateHtml()
  }

  fun renderHtmlDocument(markdownText: @NlsSafe String): @NlsSafe String {
    val body = renderHtmlBody(markdownText)
    return if (body.isBlank()) "" else "<html>$body</html>"
  }
}

private class AgentAcpThreadMessageMarkdownFlavourDescriptor : GFMFlavourDescriptor() {
  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
    return super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap().apply {
      this[MarkdownElementTypes.EMPH] = SimpleInlineTagProvider("i", 1, -1)
      this[MarkdownElementTypes.STRONG] = SimpleInlineTagProvider("b", 2, -2)
      this[GFMElementTypes.STRIKETHROUGH] = SimpleInlineTagProvider("strike", 2, -2)
      this[MarkdownElementTypes.CODE_SPAN] = EscapedCodeSpanGeneratingProvider
      this[MarkdownElementTypes.CODE_BLOCK] = EscapedCodeBlockGeneratingProvider
      this[MarkdownElementTypes.CODE_FENCE] = EscapedCodeFenceGeneratingProvider
      this[MarkdownElementTypes.HTML_BLOCK] = EscapedHtmlGeneratingProvider
      this[MarkdownTokenTypes.HTML_TAG] = EscapedHtmlGeneratingProvider
    }
  }
}

private object EscapedHtmlGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeHtml(escapeHtml(node.getTextInNode(text)))
  }
}

private object EscapedCodeSpanGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val content = if (node.children.size > 2) {
      node.children
        .subList(1, node.children.size - 1)
        .joinToString(separator = "") { it.getTextInNode(text) }
        .trim()
    }
    else {
      node.getTextInNode(text).toString()
    }
    visitor.consumeTagOpen(node, "code")
    visitor.consumeHtml(escapeHtml(content))
    visitor.consumeTagClose("code")
  }
}

private object EscapedCodeBlockGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeHtml("<pre>")
    visitor.consumeTagOpen(node, "code")
    for (child in node.children) {
      when (child.type) {
        MarkdownTokenTypes.CODE_LINE -> visitor.consumeHtml(escapeHtml(HtmlGenerator.trimIndents(child.getTextInNode(text), 4)))
        MarkdownTokenTypes.EOL -> visitor.consumeHtml("\n")
      }
    }
    visitor.consumeHtml("\n")
    visitor.consumeTagClose("code")
    visitor.consumeHtml("</pre>")
  }
}

private object EscapedCodeFenceGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length
    visitor.consumeHtml("<pre>")

    var state = 0
    val children = if (node.children.lastOrNull()?.type == MarkdownTokenTypes.CODE_FENCE_END) {
      node.children.dropLast(1)
    }
    else {
      node.children
    }
    val attributes = mutableListOf<String>()
    var lastChildWasContent = false
    for (child in children) {
      if (state == 1 && child.type in CODE_FENCE_BODY_TYPES) {
        val content = if (child.type == MarkdownTokenTypes.EOL) {
          "\n"
        }
        else {
          HtmlGenerator.trimIndents(child.getTextInNode(text), indentBefore)
        }
        visitor.consumeHtml(escapeHtml(content))
        lastChildWasContent = child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
      }
      if (state == 0 && child.type == MarkdownTokenTypes.FENCE_LANG) {
        child.getTextInNode(text).toString().trim().split(' ', limit = 2).firstOrNull()
          ?.takeIf { it.isNotBlank() }
          ?.let { language -> attributes.add("class=\"language-${escapeHtmlAttribute(language)}\"") }
      }
      if (state == 0 && child.type == MarkdownTokenTypes.EOL) {
        visitor.consumeTagOpen(node, "code", *attributes.toTypedArray())
        state = 1
      }
    }
    if (state == 0) {
      visitor.consumeTagOpen(node, "code", *attributes.toTypedArray())
    }
    if (lastChildWasContent) {
      visitor.consumeHtml("\n")
    }
    visitor.consumeHtml("</code></pre>")
  }
}

private fun escapeHtml(text: CharSequence): String = buildString(text.length) {
  for (ch in text) {
    when (ch) {
      '&' -> append("&amp;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      else -> append(ch)
    }
  }
}

private fun escapeHtmlAttribute(text: CharSequence): String = buildString(text.length) {
  for (ch in text) {
    when (ch) {
      '&' -> append("&amp;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      '"' -> append("&quot;")
      else -> append(ch)
    }
  }
}

private val CODE_FENCE_BODY_TYPES = setOf(MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL)
