// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.paragraph

import com.intellij.ide.ui.text.parts.TextPart
import com.intellij.ui.scale.JBUIScale
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
  private val unscaledAttributes: SimpleAttributeSet = SimpleAttributeSet().apply {
    StyleConstants.setRightIndent(this, NO_INDENT)
    StyleConstants.setLeftIndent(this, NO_INDENT)
    StyleConstants.setSpaceAbove(this, MEDIUM_INDENT)
    StyleConstants.setSpaceBelow(this, NO_INDENT)
    StyleConstants.setLineSpacing(this, 0.3f)
  }

  open val attributes: SimpleAttributeSet
    get() = SimpleAttributeSet().apply {
      val unscaled = unscaledAttributes
      StyleConstants.setRightIndent(this, JBUIScale.scale(StyleConstants.getRightIndent(unscaled)))
      StyleConstants.setLeftIndent(this, JBUIScale.scale(StyleConstants.getLeftIndent(unscaled)))
      StyleConstants.setSpaceAbove(this, JBUIScale.scale(StyleConstants.getSpaceAbove(unscaled)))
      StyleConstants.setSpaceBelow(this, JBUIScale.scale(StyleConstants.getSpaceBelow(unscaled)))

      StyleConstants.setLineSpacing(this, StyleConstants.getLineSpacing(unscaled))
    }

  private val partRanges: MutableList<IntRange> = mutableListOf()

  fun editAttributes(edit: SimpleAttributeSet.() -> Unit): TextParagraph {
    unscaledAttributes.edit()
    return this
  }

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
    val curAttributes = attributes
    if (!isLast) {
      textPane.document.insertString(curOffset, "\n", curAttributes)
      curOffset++
    }
    textPane.styledDocument.setParagraphAttributes(startOffset, curOffset - startOffset, curAttributes, true)
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
    const val NO_INDENT: Float = 0f
    const val SMALL_INDENT: Float = 4f
    const val MEDIUM_INDENT: Float = 8f
    const val BIG_INDENT: Float = 12f
  }
}