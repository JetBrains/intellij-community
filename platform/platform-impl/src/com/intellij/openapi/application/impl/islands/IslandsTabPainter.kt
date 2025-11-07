// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.impl.EditorTabPainterAdapter
import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.DefaultTabPainterAdapter
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.ui.tabs.impl.themes.DefaultTabTheme
import com.intellij.ui.tabs.impl.themes.TabTheme
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import kotlin.math.floor

internal class IslandsTabPainterAdapter(isDefault: Boolean, debugger: Boolean, var isEnabled: Boolean) : TabPainterAdapter {
  private val editorAdapter = if (isDefault) DefaultTabPainterAdapter(if (debugger) JBTabPainter.DEBUGGER else JBTabPainter.DEFAULT) else EditorTabPainterAdapter()
  private val islandsAdapter = IslandsTabPainter(isDefault, debugger)

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

    val tabLabelWidth = calcTabLabelWidth(label)
    val rect = Rectangle(tabLabelWidth, label.height)
    val g2 = g.create() as Graphics2D

    try {
      GraphicsUtil.setupAAPainting(g2)

      tabs.setFirstTabOffset(JBUI.scale(3))
      (tabPainter as IslandsTabPainter).paintTab(g2, rect, info.tabColor, active, hovered, selected)
    }
    finally {
      g2.dispose()
    }
  }

  /**
   * label.preferredSize doesn't work for squeeze mode
   */
  private fun calcTabLabelWidth(label: TabLabel): Int {
    var rect: Rectangle? = null

    for (component in label.components) {
      if (rect == null) {
        rect = component.bounds
      }
      else {
        rect = rect.union(component.bounds)
      }
    }

    val contentWidth = if (rect == null) 0 else rect.x + rect.width
    return contentWidth + label.insets.right
  }
}

private class IslandsTabTheme : TabTheme {
  override val background: Color
    get() = JBUI.CurrentTheme.EditorTabs.background()

  override val borderColor: Color
    get() = JBColor.namedColor("EditorTabs.underTabsBorderColor", JBUI.CurrentTheme.EditorTabs.borderColor())

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

internal open class IslandsTabPainter(isDefault: Boolean, isToolWindow: Boolean) : JBTabPainter {
  private val myTheme = when {
    isToolWindow -> object : DefaultTabTheme() {
      override val background: Color
        get() = JBUI.CurrentTheme.ToolWindow.background()
    }

    isDefault -> DefaultTabTheme()

    else -> IslandsTabTheme()
  }

  private val myFillBackground = isToolWindow || !isDefault

  override fun getTabTheme(): TabTheme = myTheme

  override fun getBackgroundColor(): Color = myTheme.background!!

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    paintTab(g, rect, tabColor, active, hovered, false)
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    paintTab(g, rect, tabColor, active, hovered, true)
  }

  override fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point) {
    g.paint2DLine(from, to, LinePainter2D.StrokeType.INSIDE, thickness.toDouble(), myTheme.borderColor)
  }

  override fun paintUnderline(position: JBTabsPosition, rect: Rectangle, borderThickness: Int, g: Graphics2D, active: Boolean) {
  }

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    if (myFillBackground) {
      fillBackground(g, rect, getBackgroundColor())
    }
  }

  override fun fillBackground(component: Component, g: Graphics2D, rect: Rectangle) {
    fillBackground(g, rect, if (myFillBackground) getBackgroundColor() else UIUtil.getBgFillColor(component))
  }

  private fun fillBackground(g: Graphics2D, rect: Rectangle, color: Color) {
    g.color = color
    RectanglePainter2D.FILL.paint(g, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
  }

  open fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean, selected: Boolean) {
    val arc = JBUI.CurrentTheme.MainToolbar.Button.hoverArc().float.toDouble()
    val compactMode = UISettings.getInstance().compactMode

    val hOffset = JBUIScale.scale(if (compactMode) 2f else 4f).toDouble()

    val fullHeight = JBUIScale.scale(if (compactMode) 24f else 28f).toDouble()
    val vOffset = (rect.height - fullHeight).coerceAtLeast(JBUIScale.scale(8f).toDouble())

    val x = rect.x + hOffset
    val y = floor(rect.y + vOffset / 2.0)
    val width = rect.width - hOffset * 2.0
    val height = rect.height - vOffset

    if (tabColor != null) {
      g.color = ColorUtil.withAlpha(tabColor, 0.9)
      RectanglePainter2D.FILL.paint(g, x, y, width, height, arc)
    }

    val (fill, draw) = getColors(active, hovered, selected)

    g.color = fill
    RectanglePainter2D.FILL.paint(g, x, y, width, height, arc)

    g.color = draw
    RectanglePainter2D.DRAW.paint(g, x, y, width, height, arc)
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