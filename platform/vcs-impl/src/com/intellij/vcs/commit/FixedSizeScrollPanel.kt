// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension

@ApiStatus.Internal
class FixedSizeScrollPanel(view: Component, private val fixedSize: Dimension) : JBScrollPane(view) {
  init {
    border = JBUI.Borders.empty()
    viewportBorder = JBUI.Borders.empty()
    isOpaque = false
    horizontalScrollBar.isOpaque = false
    verticalScrollBar.isOpaque = false
    viewport.isOpaque = false
    isOverlappingScrollBar = true
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    if (size.width > fixedSize.width) {
      size.width = fixedSize.width
      if (size.height < horizontalScrollBar.height * 2) {
        size.height = horizontalScrollBar.height * 2 // better handling of a transparent scrollbar for a single text line
      }
    }
    if (size.height > fixedSize.height) {
      size.height = fixedSize.height
    }
    return size
  }
}
