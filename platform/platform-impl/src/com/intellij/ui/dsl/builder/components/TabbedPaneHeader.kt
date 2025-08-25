// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI
import com.intellij.openapi.application.impl.InternalUICustomization.Companion.getInstance
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle

@Suppress("ReplaceRangeToWithRangeUntil")
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

  override fun getBaseline(width: Int, height: Int): Int {
    var result = -1

    for (i in 0..tabCount - 1) {
      val component = getTabComponentAt(i)
      val baseline = component.getBaseline(component.width, component.height)
      if (baseline >= 0) {
        val baselineInParent = component.y + baseline
        if (result < 0) {
          result = baselineInParent
        }
        else {
          if (result != baselineInParent) {
            return -1
          }
        }
      }
    }

    return result
  }
}

private class HeaderTabbedPaneUI : DarculaTabbedPaneUI() {
  override fun paintTabBackground(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
    val customization = getInstance()
    if (customization != null && customization.paintTab(g, Rectangle(x, y, w, h), tabIndex == hoverTab, isSelected)) {
      return
    }
    super.paintTabBackground(g, tabPlacement, tabIndex, x, y, w, h, isSelected)
  }

  override fun paintTabBorder(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
    val customization = getInstance()
    if (customization != null && customization.paintTabBorder(g, tabPlacement, tabIndex, x, y, w, h, isSelected)) {
      return
    }
    super.paintTabBorder(g, tabPlacement, tabIndex, x, y, w, h, isSelected)
  }

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

  override fun getTabLabelShiftY(tabPlacement: Int, tabIndex: Int, isSelected: Boolean): Int {
    return JBUI.scale(1)
  }
}
