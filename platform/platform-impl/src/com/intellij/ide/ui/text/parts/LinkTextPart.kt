// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import javax.swing.JTextPane
import javax.swing.text.StyleConstants

/**
 * Text part with underline that provides to execute some [runnable] action on click.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class LinkTextPart(text: String, private val runnable: () -> Unit) : TextPart(text) {
  init {
    editAttributes {
      StyleConstants.setUnderline(this, true)
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Link.Foreground.ENABLED)
    }
  }

  override val onClickAction: (JTextPane, Point, height: Int) -> Unit = { _, _, _ -> runnable() }
}