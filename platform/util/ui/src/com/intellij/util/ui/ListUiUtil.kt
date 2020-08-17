// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.JList

object ListUiUtil {
  object WithTallRow {
    private val selectionBackground: JBColor = JBColor(0xE9EEF5, 0x464A4D)
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
}