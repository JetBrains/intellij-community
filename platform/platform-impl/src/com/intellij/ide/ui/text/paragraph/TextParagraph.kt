// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.paragraph

import com.intellij.ide.ui.text.parts.TextPart
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Single paragraph of the text inside [JTextPane]. The paragraph inserts the list of [textParts] to the [JTextPane]
 *  and applies the formatting to the whole paragraph using [attributes].
 * Inserts line break after the last text part.
 * The behaviour of inserting text parts can be modified by overriding [insertToDocument] and [insertTextPart] methods.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class TextParagraph(val textParts: List<TextPart>) {
  var spaceAbove: Int = MEDIUM_INDENT
  var leftIndent: Int = NO_INDENT

  protected open val attributes: SimpleAttributeSet
    get() = SimpleAttributeSet().apply {
      StyleConstants.setRightIndent(this, 0f)
      StyleConstants.setLeftIndent(this, leftIndent.toFloat())
      StyleConstants.setSpaceAbove(this, spaceAbove.toFloat())
      StyleConstants.setSpaceBelow(this, 0f)
      StyleConstants.setLineSpacing(this, 0.2f)
    }

  private val partRanges: MutableList<IntRange> = mutableListOf()

  /**
   * Inserts this paragraph to the [textPane] from the given [startOffset] and adds line break at the end
   * @return current offset after adding the paragraph
   */
  open fun insertToDocument(textPane: JTextPane, startOffset: Int, isLast: Boolean = false): Int {
    partRanges.clear()
    var curOffset = startOffset
    for (part in textParts) {
      val start = curOffset
      curOffset = insertTextPart(part, textPane, curOffset)
      partRanges.add(start until curOffset)
    }
    if (!isLast) {
      textPane.document.insertString(curOffset, "\n", attributes)
      curOffset++
    }
    textPane.styledDocument.setParagraphAttributes(startOffset, curOffset - startOffset, attributes, true)
    return curOffset
  }

  protected open fun insertTextPart(textPart: TextPart, textPane: JTextPane, startOffset: Int): Int {
    return textPart.insertToTextPane(textPane, startOffset)
  }

  open fun findPartByOffset(offset: Int): Pair<TextPart, IntRange>? {
    val partInd = partRanges.indexOfFirst { offset in it }
    return if (partInd != -1) {
      textParts[partInd] to partRanges[partInd]
    }
    else null
  }

  companion object {
    const val NO_INDENT: Int = 0
    val SMALL_INDENT: Int
      get() = JBUI.scale(6)
    val MEDIUM_INDENT: Int
      get() = JBUI.scale(12)
    val BIG_INDENT: Int
      get() = JBUI.scale(20)
  }
}