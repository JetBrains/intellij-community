// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

private const val INLINE_BUTTON_WIDTH = 16
const val INLINE_BUTTON_MARKER = "inlineButtonMarker"

fun createExtraButton(icon: Icon, active: Boolean): JComponent {
  val label = JLabel(icon)
  label.putClientProperty(INLINE_BUTTON_MARKER, true)
  val leftRightInsets = JBUI.CurrentTheme.List.buttonLeftRightInsets()
  label.border = JBUI.Borders.empty(0, leftRightInsets)
  val panel = SelectablePanel.wrap(label)
  val size = panel.preferredSize
  size.width = buttonWidth(leftRightInsets)
  panel.preferredSize = size
  panel.minimumSize = size
  panel.selectionColor = if (active) JBUI.CurrentTheme.List.buttonHoverBackground() else null
  panel.selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
  panel.isOpaque = false
  return panel
}

fun calcButtonIndex(list: JList<*>, buttonsCount: Int, point: Point): Int? {
  val index = list.selectedIndex
  val bounds = list.getCellBounds(index, index) ?: return null
  JBInsets.removeFrom(bounds, PopupListElementRenderer.getListCellPadding())

  val distanceToRight = bounds.x + bounds.width - point.x
  val buttonsToRight = distanceToRight / buttonWidth()
  if (buttonsToRight >= buttonsCount) return null

  return buttonsCount - buttonsToRight - 1
}

@JvmOverloads
fun buttonWidth(leftRightInsets: Int = JBUI.CurrentTheme.List.buttonLeftRightInsets()) : Int = JBUIScale.scale(INLINE_BUTTON_WIDTH + leftRightInsets * 2)