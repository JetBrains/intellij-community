// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.ide.ui.text.StyledTextPaneUtils.drawRectangleAroundText
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.text.StringUtil.NON_BREAK_SPACE
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
      EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
    }
    editAttributes {
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Label.foreground())
    }
  }

  override fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    var curOffset = startOffset
    if (addSpaceAround) {
      curOffset = insertNonBreakSpace(textPane, curOffset)
    }
    textPane.document.insertString(curOffset, text, attributes)
    curOffset += text.length
    if (addSpaceAround) {
      curOffset = insertNonBreakSpace(textPane, curOffset)
    }

    val highlightStart = if (addSpaceAround) startOffset + 2 else startOffset
    val highlightEnd = if (addSpaceAround) curOffset - 2 else curOffset
    textPane.highlighter.addHighlight(highlightStart, highlightEnd) { g, _, _, _, c ->
      c.drawRectangleAroundText(highlightStart, highlightEnd, g, frameColor, fontGetter(), fill = false)
    }
    return curOffset
  }

  private fun insertNonBreakSpace(textPane: JTextPane, startOffset: Int): Int {
    return RegularTextPart("$NON_BREAK_SPACE$NON_BREAK_SPACE").insertToTextPane(textPane, startOffset)
  }
}