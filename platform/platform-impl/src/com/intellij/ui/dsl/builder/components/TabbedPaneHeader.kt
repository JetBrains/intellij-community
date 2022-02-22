// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI
import com.intellij.ui.components.JBTabbedPane
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

@ApiStatus.Internal
internal class TabbedPaneHeader : JBTabbedPane() {

  private var preferredSizeCalculation = false

  init {
    setUI(HeaderTabbedPaneUI())
  }

  override fun getSize(): Dimension {
    return if (preferredSizeCalculation) Dimension(2000, 100) else super.getSize()
  }

  override fun getPreferredSize(): Dimension {
    // Allow aligning tabs in large area
    preferredSizeCalculation = true
    try {
      val lastTabBounds = getBoundsAt(tabCount - 1)
      return Dimension(lastTabBounds.x + lastTabBounds.width, (ui as HeaderTabbedPaneUI).getTabHeight(tabPlacement))
    }
    finally {
      preferredSizeCalculation = false
    }
  }
}

private class HeaderTabbedPaneUI : DarculaTabbedPaneUI() {

  fun getTabHeight(tabPlacement: Int): Int {
    return calculateMaxTabHeight(tabPlacement)
  }

  override fun getOffset(): Int {
    return 0
  }
}
