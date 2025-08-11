// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.fileEditor.impl.EditorTabPainterAdapter
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.ui.tabs.impl.themes.TabTheme
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.RoundRectangle2D

internal class IslandsTabPainterAdapter(var isEnabled: Boolean) : TabPainterAdapter {
  private val editorAdapter = EditorTabPainterAdapter()
  private val islandsAdapter = IslandsTabPainter()

  override val tabPainter: JBTabPainter
    get() {
      return if (isEnabled) islandsAdapter else editorAdapter.tabPainter
    }

  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    if (!isEnabled) {
      tabs.setFirstTabOffset(0)
      editorAdapter.paintBackground(label, g, tabs)
      return
    }

    val info = label.info
    val selected = info == tabs.selectedInfo
    val active = tabs.isActiveTabs(info)
    val hovered = tabs.isHoveredTab(label)

    val rect = Rectangle(label.size)
    val g2 = g.create() as Graphics2D

    try {
      GraphicsUtil.setupAAPainting(g2)

      tabPainter.fillBackground(g2, rect)

      tabs.setFirstTabOffset(5)
      val accentedRect = Rectangle(rect.x, rect.y, rect.width, rect.height)
      JBInsets.removeFrom(accentedRect, JBInsets(5, 3, 5, 3))

      (tabPainter as IslandsTabPainter).paintTab(g2, accentedRect, info.tabColor, active, hovered, selected)
    }
    finally {
      g2.dispose()
    }
  }
}

private class IslandsTabTheme : TabTheme {
  override val background: Color
    get() = JBUI.CurrentTheme.EditorTabs.background()

  override val borderColor: Color
    get() = background

  override val underlineColor: Color
    get() = background

  override val inactiveUnderlineColor: Color
    get() = background

  override val hoverBackground: Color
    get() = background

  override val hoverInactiveBackground: Color? = null
  override val underlinedTabBackground: Color? = null
  override val underlinedTabInactiveBackground: Color? = null
  override val inactiveColoredTabBackground: Color? = null

  override val underlineHeight: Int = 0

  override val underlinedTabForeground: Color
    get() = JBUI.CurrentTheme.EditorTabs.underlinedTabForeground()

  override val underlinedTabInactiveForeground: Color?
    get() = JBColor.namedColor("EditorTabs.underlinedTabInactiveForeground", JBColor(0x000000, 0xFFFFFF))
}

private class IslandsTabPainter : JBTabPainter {
  private val myTheme = IslandsTabTheme()

  override fun getTabTheme(): TabTheme = myTheme

  override fun getBackgroundColor(): Color = myTheme.background

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    paintTab(g, rect, tabColor, active, hovered, false)
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    paintTab(g, rect, tabColor, active, hovered, true)
  }

  override fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point) {
  }

  override fun paintUnderline(position: JBTabsPosition, rect: Rectangle, borderThickness: Int, g: Graphics2D, active: Boolean) {
  }

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = getBackgroundColor()
    RectanglePainter2D.FILL.paint(g, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
  }

  fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean, selected: Boolean) {
    val arc = JBUI.CurrentTheme.MainToolbar.Button.hoverArc().float
    val shape = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc)

    if (tabColor != null) {
      g.color = ColorUtil.withAlpha(tabColor, 0.9)
      g.fill(shape)
    }

    val (fill, draw) = getColors(active, hovered, selected)

    g.color = fill
    g.fill(shape)

    g.color = draw
    g.draw(shape)
  }

  private val hoverBackground = JBColor("EditorTabs.hoverBackground", JBColor(Color(0xE5, 0xEE, 0xFF, 0x80), Color(0x34, 0x3E, 0x51, 0x80)))

  private val inactiveBorderColor = JBColor("EditorTabs.inactiveUnderlinedTabBorderColor", JBColor(Color(0x7F, 0x99, 0xC3, 0x80), Color(0x7F, 0x99, 0xC3, 0x80)))

  private val regularColors = JBColor("EditorTabs.regularBackground", null) to JBColor("EditorTabs.regularBorderColor", null)

  private val hoveredColors = hoverBackground to JBColor("EditorTabs.hoverBorderColor", null)

  private val selectedColors =
    JBColor("EditorTabs.underlinedTabBackground", JBColor(0xE5EEFF, 0x343E51)) to
      JBColor("EditorTabs.underlinedBorderColor", JBColor(0x7F99C3, 0x7F99C3))

  private val selectedInactiveColors = JBColor("EditorTabs.inactiveUnderlinedTabBackground", null) to inactiveBorderColor

  private val selectedHoveredInactiveColors = hoverBackground to inactiveBorderColor

  private fun getColors(active: Boolean, hovered: Boolean, selected: Boolean): Pair<Color, Color> {
    if (selected) {
      if (active) {
        return selectedColors
      }
      if (hovered) {
        return selectedHoveredInactiveColors
      }
      return selectedInactiveColors
    }
    if (hovered) {
      return hoveredColors
    }
    return regularColors
  }
}