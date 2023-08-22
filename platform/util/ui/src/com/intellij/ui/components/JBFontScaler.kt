// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.util.ui.JBFont
import java.awt.Font


class JBFontScaler(private val origFont: Font) {
  private val originalDefaultSize = JBFont.labelFontSize()
  private val currentDefaultSize get() = JBFont.labelFontSize()

  fun scaledFont(): Font {
    return if (originalDefaultSize == currentDefaultSize || origFont is JBFont) origFont
    else {
      val newSize =
        if (origFont.size == originalDefaultSize) currentDefaultSize.toFloat()
        else JBFont.scaleFontSize(origFont.size.toFloat(), currentDefaultSize.toFloat() / originalDefaultSize)

      origFont.deriveFont(newSize)
    }
  }
}
