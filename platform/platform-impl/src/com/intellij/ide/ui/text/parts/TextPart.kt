// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.Point
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Abstract text part that provides following abilities:
 *  1. Insert text to the [JTextPane] using [attributes] formatting.
 *  2. Add highlighting to the inserted text using [JTextPane.highlighter].
 *  3. Customize on click action by overriding [onClickAction].
 *
 * Text parts should be contained inside [com.intellij.ide.ui.text.paragraph.TextParagraph].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
abstract class TextPart(val text: String) {

  val attributes: SimpleAttributeSet = SimpleAttributeSet()
    get() = field.apply {
      val font = fontGetter()
      StyleConstants.setFontFamily(this, font.name)
      StyleConstants.setFontSize(this, font.size)
    }

  var fontGetter: () -> Font = { JBFont.label() }

  inline fun editAttributes(edit: SimpleAttributeSet.() -> Unit): TextPart {
    attributes.edit()
    return this
  }

  /**
   * Inserts this text part to the [textPane] from the given [startOffset]
   * @return current offset after adding the text part
   */
  open fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    val textToInsert = text
    textPane.document.insertString(startOffset, textToInsert, attributes)
    return startOffset + textToInsert.length
  }

  open val onClickAction: ((JTextPane, Point, height: Int) -> Unit)? = null

  override fun toString(): String {
    return text
  }
}