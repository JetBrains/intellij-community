// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Basic text part that inserts simple non-interactive text without highlighting.
 * @param isHeader if true the text will be 3 points larger
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class RegularTextPart(text: String, val isBold: Boolean = false, val isHeader: Boolean = false) : TextPart(text) {
  var textColor: Color = JBUI.CurrentTheme.Label.foreground()

  override val attributes: SimpleAttributeSet
    get() = SimpleAttributeSet().apply {
      val font = if (isHeader) JBFont.h3() else JBFont.label()
      StyleConstants.setFontFamily(this, font.name)
      StyleConstants.setFontSize(this, font.size)
      StyleConstants.setForeground(this, textColor)
      if (isBold) {
        StyleConstants.setBold(this, true)
      }
    }
}