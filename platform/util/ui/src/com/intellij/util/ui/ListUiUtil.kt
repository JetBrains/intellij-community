// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.event.*
import javax.swing.JList
import javax.swing.SwingUtilities

object ListUiUtil {
  object WithTallRow {
    private val selectionBackground: JBColor = JBColor(0xEDF6FE, 0x464A4D)
    private val unfocusedSelectionBackground: JBColor = JBColor(0xF5F5F5, 0x464A4D)
    private val alternativeRowBackground: JBColor = JBColor(0xFFFFFF, 0x313335)

    fun foreground(isSelected: Boolean, hasFocus: Boolean): Color {
      val default = UIUtil.getListForeground()
      return if (isSelected) {
        if (hasFocus) JBColor.namedColor("Table.lightSelectionForeground", default)
        else JBColor.namedColor("Table.lightSelectionInactiveForeground", default)
      }
      else JBColor.namedColor("Table.foreground", default)
    }

    fun secondaryForeground(list: JList<*>, isSelected: Boolean): Color {
      return if (isSelected) {
        foreground(true, list.hasFocus())
      }
      else JBColor.namedColor("Component.infoForeground", UIUtil.getContextHelpForeground())
    }

    fun background(list: JList<*>, isSelected: Boolean, hasFocus: Boolean): Color {
      return if (isSelected) {
        if (hasFocus) JBColor.namedColor("Table.lightSelectionBackground", selectionBackground)
        else JBColor.namedColor("Table.lightSelectionInactiveBackground", unfocusedSelectionBackground)
      }
      else list.background
    }

    fun alternativeBackground(isSelected: Boolean, hasFocus: Boolean): Color {
      return if (isSelected) {
        if (hasFocus) JBColor.namedColor("Table.lightSelectionBackground", selectionBackground)
        else JBColor.namedColor("Table.lightSelectionInactiveBackground", unfocusedSelectionBackground)
      }
      else {
        JBColor.namedColor("Table.alternativeRowBackground", alternativeRowBackground)
      }
    }
  }

  object Selection {
    fun installSelectionOnFocus(list: JList<*>): FocusListener {
      val listener: FocusListener = object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          if (list.isSelectionEmpty && list.model.size > 0) {
            list.selectedIndex = 0
          }
        }
      }
      list.addFocusListener(listener)
      return listener
    }

    fun installSelectionOnRightClick(list: JList<*>): MouseListener {
      val listener: MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isRightMouseButton(e)) {
            val row = list.locationToIndex(e.point)
            if (row != -1) list.selectedIndex = row
          }
        }
      }
      list.addMouseListener(listener)
      return listener
    }
  }
}