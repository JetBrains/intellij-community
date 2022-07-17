// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI
import com.intellij.ui.components.JBTabbedPane
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

@ApiStatus.Internal
internal class TabbedPaneHeader : JBTabbedPane() {

  override fun updateUI() {
    setUI(HeaderTabbedPaneUI())
  }

  override fun getPreferredSize(): Dimension {
    val insets = insets
    val tabbedPaneUI = ui as HeaderTabbedPaneUI
    return Dimension(tabbedPaneUI.getTabsWidth() + insets.left + insets.right,
                     tabbedPaneUI.getTabHeight() + insets.top + insets.bottom)
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }
}

private class HeaderTabbedPaneUI : DarculaTabbedPaneUI() {

  fun getTabsWidth(): Int {
    val metrics = tabPane.getFontMetrics(tabPane.font)
    return (0 until tabPane.tabCount).sumOf { calculateTabWidth(tabPane.tabPlacement, it, metrics) }
  }

  fun getTabHeight(): Int {
    return calculateMaxTabHeight(tabPane.tabPlacement)
  }

  override fun getOffset(): Int {
    return 0
  }
}
