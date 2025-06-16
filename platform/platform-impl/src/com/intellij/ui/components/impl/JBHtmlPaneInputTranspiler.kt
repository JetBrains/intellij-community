// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl

import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.impl.JBHtmlPaneStyleSheetRulesProvider.Companion.buildCodeBlock
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.Nls
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor
import javax.swing.KeyStroke

internal object JBHtmlPaneInputTranspiler {

  private val dropPrecedingEmptyParagraphTags = CollectionFactory.createCharSequenceSet(false).also {
    it.addAll(listOf("ul", "ol", "dl", "h1", "h2", "h3", "h4", "h5", "h6", "p", "tr", "td",
                     "table", "pre", "blockquote", "div", "details", "summary"))
  }

  /**
   * Transpiler pane input to fit to limited AWT HTML toolkit support.
   */
  fun transpileHtmlPaneInput(text: @Nls String): @Nls String {
    val document = Jsoup.parse(text)
    document.traverse(NodeVisitor { node, _ ->
      when (node) {
        is TextNode -> {
          transpileTextNode(node)
        }
        is Element -> {
          when {
            node.nameIs("p") -> transpileParagraph(node)
            node.nameIs("shortcut") -> transpileShortcut(node)
            node.nameIs("blockquote") -> transpileBlockquote(node)
            node.nameIs("pre") -> transpilePre(node)
            node.nameIs("icon") -> transpileIcon(node)
          }
        }
      }
    })
    sanitizeTables(document)
    document.outputSettings().prettyPrint(false)
    return document.html()
  }

  /**
   * Remove empty `<p>` before some tags - workaround for Swing html renderer not removing empty paragraphs before non-inline tags
   */
  private fun transpileParagraph(node: Element) {
    if (node.childNodeSize() == 0
        || (node.childNodeSize() == 1
            && node.childNode(0).asSafely<TextNode>()?.wholeText?.isBlank() == true)
        && node.nextElementSibling()?.let { dropPrecedingEmptyParagraphTags.contains(it.tagName()) } == true
    ) {
      node.remove()
    }
  }

  /**
   * Expand `<shortcut raw|actionId="*"/>` tag into a sequence of `<kbd>` tags
   */
  @Suppress("HardCodedStringLiteral")
  private fun transpileShortcut(node: Element) {
    val actionId = node.attributes().getIgnoreCase("actionid")
      .takeIf { it.isNotEmpty() }
    val raw = node.attributes().getIgnoreCase("raw")
      .takeIf { it.isNotEmpty() }

    if (actionId != null || raw != null) {
      val shortcutData =
        if (actionId != null)
          ShortcutsRenderingUtil.getShortcutByActionId(actionId)
            ?.let { ShortcutsRenderingUtil.getKeyboardShortcutData(it) }?.first
          ?: ShortcutsRenderingUtil.getGotoActionData(actionId, false)
            .takeIf { ActionManager.getInstance().getAction(actionId) != null }
            ?.first
        else
          KeyStroke.getKeyStroke(raw)
            ?.let { ShortcutsRenderingUtil.getKeyStrokeData(it) }
            ?.first
      if (shortcutData != null) {
        val replacement = shortcutData
          .splitToSequence(ShortcutsRenderingUtil.SHORTCUT_PART_SEPARATOR)
          .fold(mutableListOf<Node>()) { acc, s ->
            if (acc.isNotEmpty()) {
              acc.add(TextNode(StringUtil.NON_BREAK_SPACE))
            }
            acc.add(Element("kbd").text(s))
            acc
          }
        node.replaceWith(replacement)
      }
      else {
        node.replaceWith(Element("kbd").text(actionId ?: raw!!))
      }
    }
  }

  /**
   * Replace `<pre><code>(...)</code></pre>` with [JBHtmlPaneStyleSheetRulesProvider.buildCodeBlock]
   */
  private fun transpilePre(node: Element) {
    if (node.childNodeSize() != 1) return
    val childNodes =
      node.childNode(0)
        .asSafely<Element>()
        ?.takeIf { it.nameIs("code") }
        ?.childNodes()
      ?: return
    node.replaceWith(buildCodeBlock(childNodes))
  }

  /**
   * Replace `<blockquote>\\s*<pre>(...)</pre>\\s*</blockquote>` with [JBHtmlPaneStyleSheetRulesProvider.buildCodeBlock]
   */
  private fun transpileBlockquote(node: Element) {
    if (node.childNodeSize() > 3 || node.childNodeSize() < 1) return
    val nodes = node.childNodes()
    val wsNode1 = nodes.getOrNull(0).asSafely<TextNode>()
    val preNode = nodes.getOrNull(if (wsNode1 == null) 0 else 1).asSafely<Element>()
                  ?: return
    val wsNode2 = nodes.getOrNull(if (wsNode1 == null) 1 else 2)

    if (wsNode1?.wholeText.isNullOrBlank()
        && preNode.nameIs("pre")
        && wsNode2.let { it == null || it is TextNode && it.wholeText.isBlank() }) {

      val preNodes = preNode.childNodes()
      preNodes.getOrNull(0)?.asSafely<TextNode>()?.let {
        it.text(it.wholeText.trim('\n', '\r'))
      }
      preNodes.lastOrNull()?.asSafely<TextNode>()?.let {
        it.text(it.wholeText.trimEnd())
      }
      node.replaceWith(buildCodeBlock(preNodes))
    }
  }

  /**
   * Move icon children to parent node
   */
  private fun transpileIcon(node: Element) {
    node.parent()
      ?.insertChildren(node.siblingIndex() + 1, node.childNodes())
  }

  /**
   * - Add `<wbr>` after `.` if surrounded by letters
   * - Add `<wbr>` after `]`, `)` or `/` followed by a char or digit
   */
  private fun transpileTextNode(node: TextNode) {
    val builder = StringBuilder()

    val text = node.wholeText
    val codePoints = text.codePoints().iterator()
    if (!codePoints.hasNext()) return
    var codePoint = codePoints.nextInt()

    fun next() {
      builder.appendCodePoint(codePoint)
      codePoint = if (codePoints.hasNext())
        codePoints.nextInt()
      else
        -1
    }

    val replacement = SmartList<Node>()

    while (codePoint >= 0) {
      when {
        // break after dot if surrounded by letters
        Character.isLetter(codePoint) -> {
          next()
          if (codePoint == '.'.code) {
            next()
            if (Character.isLetter(codePoint)) {
              replacement.add(TextNode(builder.toString()))
              replacement.add(Element("wbr"))
              builder.clear()
            }
          }
        }
        // break after ], ) or / followed by a char or digit
        codePoint == ')'.code || codePoint == ']'.code || codePoint == '/'.code -> {
          next()
          if (Character.isLetterOrDigit(codePoint)) {
            replacement.add(TextNode(builder.toString()))
            replacement.add(Element("wbr"))
            builder.clear()
          }
        }
        else -> next()
      }
    }
    if (!replacement.isEmpty()) {
      replacement.add(TextNode(builder.toString()))
      node.replaceWith(replacement)
    }
  }

  private fun Node.replaceWith(nodes: List<Node>) {
    val parent = parent() as? Element ?: return
    parent.insertChildren(siblingIndex(), nodes)
    remove()
  }

  /**
   * IJPL-160370 - JEditorPane with HtmlToolkit crashes when there is text within the <table> tag.
   * Move it before the table as browsers do.
   */
  private fun sanitizeTables(document: Document) {
    document.select("table").forEach { table ->
      table.textNodes().forEach {
        if (!it.isBlank) {
          it.remove()
          table.before(it)
        }
      }
    }
  }
}