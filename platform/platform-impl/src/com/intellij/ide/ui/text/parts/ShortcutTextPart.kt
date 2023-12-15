// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.ide.ui.text.StyledTextPaneUtils.drawRectangleAroundText
import com.intellij.ide.ui.text.showActionKeyPopup
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Point
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Text part that gets the shortcut from provided [text] and inserts it to the [JTextPane] with non break spaces between the shortcut parts.
 * Also, it draws the filled rounded rectangle around each part of the shortcut.
 * If provided [text] is an action id it also will show popup with other shortcuts on click.
 *
 * @param isRaw true value means that [text] is a raw shortcut (in a format described in [KeyStroke.getKeyStroke]),
 *  otherwise [text] should contain id of the action.
 *
 * [delimiter] - text part that will be added before and after the computed shortcut.
 * It is required because highlighting is wider than text bounds.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class ShortcutTextPart(text: String, val isRaw: Boolean) : TextPart(text) {
  var backgroundColor: Color = JBColor.namedColor("Lesson.shortcutBackground", 0xE6EEF7, 0x333638)
  var delimiter: TextPart = RegularTextPart("")

  init {
    editAttributes {
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Label.foreground())
      StyleConstants.setBold(this, true)
    }
  }

  private val separatorAttributes: SimpleAttributeSet
    get() = SimpleAttributeSet().apply {
      val font = fontGetter()
      StyleConstants.setFontFamily(this, font.name)
      StyleConstants.setFontSize(this, font.size)
      StyleConstants.setForeground(this, UIUtil.getLabelInfoForeground())
    }

  override fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    val (shortcut, split) = getShortcutData()
    var curOffset = startOffset
    var start = 0
    val shortcutAttributes = attributes
    val sepAttributes = separatorAttributes
    curOffset = delimiter.insertToTextPane(textPane, curOffset)
    for (part in split) {
      curOffset = insertText(textPane, shortcut.substring(start, part.first), curOffset, sepAttributes)

      val partStart = curOffset
      curOffset = insertText(textPane, shortcut.substring(part.first, part.last + 1), curOffset, shortcutAttributes)
      val partEnd = curOffset

      textPane.highlighter.addHighlight(partStart, partEnd) { g, _, _, _, c ->
        c.drawRectangleAroundText(partStart, partEnd, g, backgroundColor, fontGetter(), fontGetter(), fill = true, 0.5f)
      }
      start = part.last + 1
    }
    curOffset = insertText(textPane, shortcut.substring(start), curOffset, sepAttributes)
    curOffset = delimiter.insertToTextPane(textPane, curOffset)
    return curOffset
  }

  override val onClickAction: ((JTextPane, Point, height: Int) -> Unit)?
    get() {
      return if (!isRaw) {
        { textPane: JTextPane, point: Point, height: Int ->
          showActionKeyPopup(textPane, point, height, text)
        }
      }
      else null
    }

  private fun insertText(textPane: JTextPane, text: String, insertOffset: Int, attributes: AttributeSet): Int {
    textPane.document.insertString(insertOffset, text, attributes)
    return insertOffset + text.length
  }

  private fun getShortcutData(): Pair<String, List<IntRange>> {
    return if (!isRaw) {
      val actionId = text
      val shortcut = ShortcutsRenderingUtil.getShortcutByActionId(actionId)
      if (shortcut != null) {
        ShortcutsRenderingUtil.getKeyboardShortcutData(shortcut)
      }
      else ShortcutsRenderingUtil.getGotoActionData(actionId, true)
    }
    else {
      val keyStroke = KeyStroke.getKeyStroke(text)
      if (keyStroke != null) {
        ShortcutsRenderingUtil.getKeyStrokeData(keyStroke)
      }
      else ShortcutsRenderingUtil.getRawShortcutData(text)
    }
  }
}