// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text.parts

import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.text.StyleConstants

/**
 * Basic text part that inserts simple non-interactive text without highlighting.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class RegularTextPart(text: String, val isBold: Boolean = false) : TextPart(text) {
  init {
    editAttributes {
      StyleConstants.setForeground(this, JBUI.CurrentTheme.Label.foreground())
      StyleConstants.setBold(this, isBold)
    }
  }
}