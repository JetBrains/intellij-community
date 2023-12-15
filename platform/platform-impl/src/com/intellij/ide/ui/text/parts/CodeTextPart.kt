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
 * [delimiter] - text part that will be added before and after the provided [text].
 * It is required because highlighting is wider than text bounds.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class CodeTextPart(text: String) : TextPart(text) {
  var frameColor: Color = JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false)
  var delimiter: TextPart = RegularTextPart("")

  init {
    fontGetter = {
      EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
    }
    editAttributes {
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Label.foreground())
    }
  }

  override fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    var curOffset = startOffset
    curOffset = delimiter.insertToTextPane(textPane, curOffset)
    textPane.document.insertString(curOffset, text, attributes)
    curOffset += text.length
    curOffset = delimiter.insertToTextPane(textPane, curOffset)

    val delimiterLength = delimiter.text.length
    val highlightStart = startOffset + delimiterLength
    val highlightEnd = curOffset - delimiterLength
    textPane.highlighter.addHighlight(highlightStart, highlightEnd) { g, _, _, _, c ->
      c.drawRectangleAroundText(highlightStart, highlightEnd, g, frameColor, fontGetter(), delimiter.fontGetter(), fill = false, -0.5f)
    }
    return curOffset
  }
}