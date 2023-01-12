// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.ui.text.paragraph.ListParagraph
import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.*
import com.intellij.ide.util.TipUtils.IconWithRoundedBorder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil.NON_BREAK_SPACE
import com.intellij.util.ui.JBFont
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.awt.Desktop
import java.net.URI
import javax.swing.Icon
import javax.swing.text.StyleConstants

internal class TipContentConverter(private val tipContent: Element,
                                   private val iconsMap: Map<String, Icon>,
                                   private val isStrict: Boolean) {
  fun convert(): List<TextParagraph> {
    val list = mutableListOf<TextParagraph>()
    for (node in tipContent.childNodes()) {
      if (node is Element) {
        convertParagraph(node)?.let { list.add(it) }
      }
      else if (node is TextNode) {
        val text = node.text()
        if (text.isNotBlank()) {
          list.add(TextParagraph(listOf(RegularTextPart(text))))
        }
      }
      else warnIfNotBlankNode(node)
    }

    return list
  }

  private fun convertParagraph(element: Element): TextParagraph? {
    return when {
      element.tagName() == "p" && element.hasClass("image") -> convertImageParagraph(element)
      element.tagName() == "p" -> convertCommonParagraph(element)
      element.tagName() == "ul" -> convertListParagraph(element)
      else -> {
        warnIfNotBlankNode(element)
        null
      }
    }
  }

  private fun convertImageParagraph(element: Element): TextParagraph? {
    for (node in element.childNodes()) {
      if (node is Element && node.tagName() == "img" && node.hasAttr("src")) {
        val path = node.attr("src")
        val icon = iconsMap[path]
        if (icon != null) {
          val roundedIcon = IconWithRoundedBorder(icon)
          return TextParagraph(listOf(IllustrationTextPart(roundedIcon))).editAttributes {
            StyleConstants.setSpaceAbove(this, TextParagraph.BIG_INDENT)
            StyleConstants.setLineSpacing(this, 0f)  // it is required to not add extra space below the image
          }
        }
        else handleWarning("Failed to find icon for path: $path")
      }
      else warnIfNotBlankNode(node)
    }
    handleWarning("Not found img node in element:\n$element")
    return null
  }

  private fun convertListParagraph(element: Element): TextParagraph? {
    val items = mutableListOf<List<TextParagraph>>()
    for (node in element.childNodes()) {
      if (node is Element && node.tagName() == "li") {
        val paragraphs = mutableListOf<TextParagraph>()
        for (child in node.childNodes()) {
          if (child is Element) {
            convertParagraph(child)?.let { paragraphs.add(it) }
          }
          else warnIfNotBlankNode(child)
        }
        if (paragraphs.isNotEmpty()) {
          items.add(paragraphs)
        }
        else handleWarning("List item doesn't contain any paragraph:\n$node")
      }
      else warnIfNotBlankNode(node)
    }

    return if (items.isNotEmpty()) {
      ListParagraph(items)
    }
    else {
      handleWarning("List doesn't contain any list item:\n$element")
      null
    }
  }

  private fun convertCommonParagraph(element: Element): TextParagraph? {
    val list = mutableListOf<TextPart>()
    for (node in element.childNodes()) {
      if (node is TextNode) {
        list.add(RegularTextPart(node.text()))
        continue
      }
      else if (node is Element) {
        val textPart = when {
          node.tagName() == "b" -> {
            RegularTextPart(getElementInnerText(node), isBold = true).apply {
              fontGetter = { JBFont.h3() }
            }
          }
          node.tagName() == "span" && node.hasClass("control") -> {
            RegularTextPart(getElementInnerText(node), isBold = true)
          }
          node.tagName() == "span" && node.hasClass("shortcut") -> {
            val text = getElementInnerText(node)
            val delimiter = RegularTextPart("$NON_BREAK_SPACE$NON_BREAK_SPACE")
            if (text.startsWith("&shortcut:")) {
              val actionId = text.removePrefix("&shortcut:").removeSuffix(";")
              if (isStrict && ActionManager.getInstance().getAction(actionId) == null) {
                handleWarning("Failed to find action with id: $actionId")
              }
              ShortcutTextPart(actionId, isRaw = false).apply { this.delimiter = delimiter }
            }
            else ShortcutTextPart(text, isRaw = true).apply { this.delimiter = delimiter }
          }
          node.tagName() == "span" && node.hasClass("code_emphasis") -> {
            val text = getElementInnerText(node).replace(" ", NON_BREAK_SPACE)
            CodeTextPart(text).apply { this.delimiter = RegularTextPart("$NON_BREAK_SPACE$NON_BREAK_SPACE") }
          }
          node.tagName() == "span" -> {
            // any other span elements: inlined product information
            RegularTextPart(getElementInnerText(node))
          }
          node.tagName() == "img" && node.hasAttr("src") -> {
            val path = node.attr("src")
            val icon = iconsMap[path]
            if (icon != null) {
              IconTextPart(icon)
            }
            else {
              handleWarning("Failed to find icon for path: $path")
              null
            }
          }
          node.tagName() == "a" && node.hasAttr("href") -> {
            val url = node.attr("href")
            LinkTextPart(getElementInnerText(node)) {
              try {
                val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
                if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                  desktop.browse(URI(url))
                }
              }
              catch (ex: Exception) {
                LOG.warn(ex)
              }
            }
          }
          else -> {
            handleWarning("Found unknown node:\n$node")
            RegularTextPart(getElementInnerText(node))
          }
        }

        if (textPart != null) {
          list.add(textPart)
        }
        else handleWarning("Failed to covert element to text part:\n$node")
      }
      else warnIfNotBlankNode(node)
    }

    return if (list.isNotEmpty()) {
      TextParagraph(list)
    }
    else {
      handleWarning("Paragraph is empty:\n$element")
      null
    }
  }

  private fun getElementInnerText(element: Element): String {
    if (element.childNodeSize() == 0) {
      handleWarning("Expected element with child node, but was:\n$element")
      return element.toString()
    }
    val child = element.childNode(0)
    return if (child is TextNode) {
      child.text()
    }
    else {
      handleWarning("Expected text node, but was:\n$child")
      child.toString()
    }
  }

  private fun warnIfNotBlankNode(node: Node) {
    if (node !is TextNode || node.text().isNotBlank()) {
      handleWarning("Found unknown node:\n$node")
    }
  }

  private fun handleWarning(message: String) = if (isStrict) throw RuntimeException("Warning: $message") else LOG.warn(message)

  companion object {
    private val LOG: Logger = Logger.getInstance(TipContentConverter::class.java)
  }
}