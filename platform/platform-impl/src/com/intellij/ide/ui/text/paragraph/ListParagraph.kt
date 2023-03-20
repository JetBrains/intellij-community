// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.paragraph

import com.intellij.ide.ui.text.paragraph.ListParagraph.Companion.LIST_POINT
import com.intellij.ide.ui.text.parts.RegularTextPart
import com.intellij.ide.ui.text.parts.TextPart
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTextPane
import javax.swing.text.StyleConstants

/**
 * Paragraph that represents the bulleted list.
 * Each list of paragraphs contained in [items] is a list element marked with [LIST_POINT]
 * Each list element can contain one paragraph or more.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ListParagraph(val items: List<List<TextParagraph>>) : TextParagraph(emptyList()) {
  override fun insertToDocument(textPane: JTextPane, startOffset: Int, isLast: Boolean): Int {
    var curOffset = startOffset
    for ((ind, itemParagraphs) in items.withIndex()) {
      val indent = if (ind == 0) BIG_INDENT else MEDIUM_INDENT
      val last = isLast && ind == items.lastIndex
      curOffset = insertListItem(itemParagraphs, indent, textPane, curOffset, last)
    }
    return curOffset
  }

  private fun insertListItem(itemParagraphs: List<TextParagraph>,
                             spaceAbove: Float,
                             textPane: JTextPane,
                             startOffset: Int,
                             isLast: Boolean): Int {
    var curOffset = startOffset
    val pointPart = RegularTextPart(LIST_POINT)
    val additionalIndent = textPane.getFontMetrics(pointPart.fontGetter()).stringWidth(LIST_POINT)
    for ((ind, paragraph) in itemParagraphs.withIndex()) {
      val last = isLast && ind == itemParagraphs.lastIndex
      if (ind == 0) {
        curOffset = pointPart.insertToTextPane(textPane, curOffset)
        curOffset = insertParagraph(paragraph, spaceAbove, MEDIUM_INDENT, textPane, curOffset, last)
      }
      else {
        curOffset = insertParagraph(paragraph, SMALL_INDENT, MEDIUM_INDENT + additionalIndent, textPane, curOffset, last)
      }
    }
    return curOffset
  }

  private fun insertParagraph(paragraph: TextParagraph,
                              spaceAbove: Float,
                              leftIndent: Float,
                              textPane: JTextPane,
                              startOffset: Int,
                              isLast: Boolean): Int {
    paragraph.editAttributes {
      StyleConstants.setSpaceAbove(this, spaceAbove)
      StyleConstants.setLeftIndent(this, leftIndent)
    }
    return paragraph.insertToDocument(textPane, startOffset, isLast)
  }

  override fun findPartByOffset(offset: Int): Pair<TextPart, IntRange>? {
    items.flatten().forEach { paragraph ->
      paragraph.findPartByOffset(offset)?.let { return it }
    }
    return null
  }

  companion object {
    private const val LIST_POINT = "\u2022  "
  }
}