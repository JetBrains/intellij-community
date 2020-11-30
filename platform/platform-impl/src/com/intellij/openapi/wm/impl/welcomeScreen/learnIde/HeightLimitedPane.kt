// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.JTextPane
import javax.swing.plaf.ComponentUI
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.TextUI
import javax.swing.text.DefaultCaret
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * This panel has limited height by its preferred size and doesn't grow more. The maximum width
 * could be limited as well by setting maximumWidth.
 */
class HeightLimitedPane(text: String, private val relativeFontSize: Int, private val style: SimpleAttributeSet, private val maximumWidth: Int? = null) : JTextPane() {
  init {
    isEditable = false
    document.insertString(0, text, style)
    //ensure that style has been applied
    StyleConstants.setFontSize(style, (font.size2D + JBUIScale.scale(relativeFontSize)).toInt())
    styledDocument.setCharacterAttributes(0, text.length, style, true)
    styledDocument.setParagraphAttributes(0, text.length, style, true)
    isOpaque = false
    isEditable = false
    alignmentX = LEFT_ALIGNMENT
    highlighter = null
    //make JTextPane transparent for mouse actions
    caret = object : DefaultCaret() {
      override fun mousePressed(e: MouseEvent?) {
        this@HeightLimitedPane.parent.mouseListeners.forEach { it.mousePressed(e) }
      }

      override fun mouseReleased(e: MouseEvent?) {
        this@HeightLimitedPane.parent.mouseListeners.forEach { it.mouseReleased(e) }
      }

      override fun mouseEntered(e: MouseEvent?) {
        this@HeightLimitedPane.parent.mouseListeners.forEach { it.mouseEntered(e) }
      }

      override fun mouseExited(e: MouseEvent?) {
        this@HeightLimitedPane.parent.mouseListeners.forEach { it.mouseExited(e) }
      }
    }
  }

  override fun getMaximumSize(): Dimension {
    if (maximumWidth == null) {
      return this.preferredSize
    }
    else {
      return Dimension(width, this.preferredSize.height)
    }
  }

  override fun setUI(ui: TextUI?) {
    super.setUI(ui)
    if (font != null) {
      font = FontUIResource(font.deriveFont(font.size2D + JBUIScale.scale(relativeFontSize)))
    }
  }

  override fun setUI(newUI: ComponentUI?) {
    super.setUI(newUI)
  }

  override fun updateUI() {
    super.updateUI()
    @Suppress("SENSELESS_COMPARISON")
    if (font != null && style != null) {
      StyleConstants.setFontSize(style, font.size)
      styledDocument.setCharacterAttributes(0, text.length, style, true)
      styledDocument.setParagraphAttributes(0, text.length, style, true)
    }
  }
}