// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.*
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

internal class EditorTabPainterAdapter : TabPainterAdapter {
  private val magicOffset = 1
  override val tabPainter: JBEditorTabPainter = JBEditorTabPainter()

  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    val info = label.info
    val isSelected = info == tabs.selectedInfo
    val isHovered = tabs.isHoveredTab(label)

    val rect = Rectangle(0, 0, label.width, label.height)

    val g2d = g as Graphics2D
    if (isSelected) {
      tabPainter.paintSelectedTab(
        position = tabs.tabsPosition,
        g = g2d,
        rect = rect,
        borderThickness = tabs.borderThickness,
        tabColor = info.tabColor,
        active = tabs.isActiveTabs(info),
        hovered = isHovered,
      )
      paintBorders(g2d, label, tabs)
    }
    else {
      if (ExperimentalUI.isNewUI() && isHovered
          && tabs.tabsPosition == JBTabsPosition.top
          && (tabs as JBEditorTabs).shouldPaintBottomBorder()) {
        rect.height -= 1
      }
      tabPainter.paintTab(
        position = tabs.tabsPosition,
        g = g2d,
        rect = rect,
        borderThickness = tabs.borderThickness,
        tabColor = info.tabColor,
        active = tabs.isActiveTabs(info),
        hovered = isHovered,
      )
      paintBorders(g2d, label, tabs)
    }
  }

  private fun paintBorders(g: Graphics2D, label: TabLabel, tabs: JBTabsImpl) {
    val paintStandardBorder = !ExperimentalUI.isNewUI() && !tabs.isSingleRow
                              || (!tabs.tabsPosition.isSide && Registry.`is`("ide.new.editor.tabs.vertical.borders"))
                              || label.isForcePaintBorders
    val lastPinned = label.isLastPinned
    val nextToLastPinned = label.isNextToLastPinned
    val rect = Rectangle(0, 0, label.width, label.height)
    if (paintStandardBorder || lastPinned || nextToLastPinned) {
      val bounds = label.bounds
      if (bounds.x > magicOffset && (paintStandardBorder || nextToLastPinned)) {
        tabPainter.paintLeftGap(tabs.tabsPosition, g, rect, tabs.borderThickness)
      }

      if (bounds.x + bounds.width < tabs.width - magicOffset
          && (!label.isLastInRow || !ExperimentalUI.isNewUI())
          && (paintStandardBorder || lastPinned)) {
        tabPainter.paintRightGap(tabs.tabsPosition, g, rect, tabs.borderThickness)
      }
    }

    if (tabs.tabsPosition.isSide && lastPinned) {
      val bounds = label.bounds
      if (bounds.y + bounds.height < tabs.height - magicOffset) {
        tabPainter.paintBottomGap(tabs.tabsPosition, g, rect, tabs.borderThickness)
      }
    }
    if (tabs.tabsPosition.isSide && nextToLastPinned) {
      val bounds = label.bounds
      if (bounds.y + bounds.height < tabs.height - magicOffset) {
        tabPainter.paintTopGap(tabs.tabsPosition, g, rect, tabs.borderThickness)
      }
    }
  }
}