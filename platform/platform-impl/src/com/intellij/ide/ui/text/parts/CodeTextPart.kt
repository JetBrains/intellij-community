// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.ide.ui.text.StyledTextPaneUtils.drawRectangleAroundText
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JTextPane
import javax.swing.text.StyleConstants

/**
 * Text part that inserts the text using current editor font and draws the rounded rectangle around.
 * @param addSpaceAround if true one space will be added before and after the provided [text].
 *  It is required because highlighting is wider than text bounds.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class CodeTextPart(text: String, private val addSpaceAround: Boolean = false) : TextPart(text) {
  var frameColor: Color = JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false)

  init {
    fontGetter = {
      EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size2D)
    }
    editAttributes {
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Label.foreground())
    }
  }

  override fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    val textToInsert = if (addSpaceAround) "\u00A0$text\u00A0" else text
    textPane.document.insertString(startOffset, textToInsert, attributes)
    val endOffset = startOffset + textToInsert.length

    val highlightStart = if (addSpaceAround) startOffset + 1 else startOffset
    val highlightEnd = if (addSpaceAround) endOffset - 1 else endOffset
    textPane.highlighter.addHighlight(highlightStart, highlightEnd) { g, _, _, _, c ->
      c.drawRectangleAroundText(highlightStart, highlightEnd, g, frameColor, fill = false)
    }
    return endOffset
  }
}