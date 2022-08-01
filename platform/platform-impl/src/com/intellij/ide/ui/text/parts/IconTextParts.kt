// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.ide.ui.text.StyledTextPaneUtils.insertIconWithFixedHeight
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Text part that inserts icon to the [JTextPane]. Assumed that icon height is small enough to fit in the line of text between other text parts.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class IconTextPart(val icon: Icon) : TextPart("") {
  override val attributes = SimpleAttributeSet()

  override fun insertToTextPane(textPane: JTextPane, startOffset: Int): Int {
    val height = textPane.getFontMetrics(textPane.font).ascent  // fake height to place icon little lower
    textPane.insertIconWithFixedHeight(icon, height)
    return startOffset + 1
  }
}

/**
 * Text part that inserts icon to the [JTextPane]. Assumed that this icon is a big one and will fill all paragraph width.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class IllustrationTextPart(val icon: Icon) : TextPart(" ") {
  override val attributes = SimpleAttributeSet().apply {
    StyleConstants.setIcon(this, icon)
  }
}