// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.util.ui.UIUtil
import javax.swing.JLabel

@Deprecated("Use Kotlin UI DSL 2, for description use {@link com.intellij.ui.dsl.builder.Cell#comment}")
class DescriptionLabel(text: String?) : JLabel() {
  init {
    setText(text)
  }

  override fun updateUI() {
    super.updateUI()
    setForeground(UIUtil.getLabelDisabledForeground())
    var size = getFont().getSize()
    if (size >= 12) {
      size -= 2
    }
    setFont(getFont().deriveFont(size.toFloat()))
  }
}