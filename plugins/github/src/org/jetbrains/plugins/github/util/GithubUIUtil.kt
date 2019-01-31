// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import java.awt.Color
import javax.swing.JList

object GithubUIUtil {
  object List {
    object WithTallRow {
      private val selectionBackground = JBColor(0xE9EEF5, 0x464A4D)
      private val unfocusedSelectionBackground = JBColor(0xF5F5F5, 0x464A4D)

      fun foreground(list: JList<*>, isSelected: Boolean): Color {
        val default = UIUtil.getListForeground()
        return if (isSelected) {
          if (list.hasFocus()) JBColor.namedColor("Table.lightSelectionForeground", default)
          else JBColor.namedColor("Table.lightSelectionInactiveForeground", default)
        }
        else JBColor.namedColor("Table.foreground", default)
      }

      fun secondaryForeground(list: JList<*>, isSelected: Boolean): Color {
        return if (isSelected) {
          foreground(list, true)
        }
        else JBColor.namedColor("Component.infoForeground", UIUtil.getContextHelpForeground())
      }

      fun background(list: JList<*>, isSelected: Boolean): Color {
        return if (isSelected) {
          if (list.hasFocus()) JBColor.namedColor("Table.lightSelectionBackground", selectionBackground)
          else JBColor.namedColor("Table.lightSelectionInactiveBackground", unfocusedSelectionBackground)
        }
        else list.background
      }
    }
  }

  fun createIssueLabelLabel(label: GithubIssueLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    val apiColor = ColorUtil.fromHex(label.color)
    background = JBColor(apiColor, ColorUtil.darker(apiColor, 3))
    foreground = computeForeground(background)
  }.andOpaque()

  private fun computeForeground(bg: Color) = if (ColorUtil.isDark(bg)) Color.white else Color.black
}