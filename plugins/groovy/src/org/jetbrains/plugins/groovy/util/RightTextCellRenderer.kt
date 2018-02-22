// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * This renderer adds grey text aligned to the right of whatever component returned by [baseRenderer]
 *
 * @see com.intellij.ide.util.PsiElementModuleRenderer
 */
class RightTextCellRenderer<T>(
  private val baseRenderer: ListCellRenderer<in T>,
  private val text: (T) -> String?
) : ListCellRenderer<T> {

  override fun getListCellRendererComponent(list: JList<out T>?,
                                            value: T?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val originalComponent = baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    if (value == null || list == null || originalComponent == null) return originalComponent

    val text = text(value) ?: return originalComponent

    val bg = if (isSelected) UIUtil.getListSelectionBackground() else originalComponent.background

    val labelComponent = JBLabel(text).apply {
      isOpaque = true
      border = JBUI.Borders.emptyRight(UIUtil.getListCellHPadding())
      background = bg
      foreground = if (isSelected) UIUtil.getListSelectionForeground() else UIUtil.getInactiveTextColor()
    }

    val spacer = JPanel().apply {
      border = JBUI.Borders.empty(0, 2)
      background = bg
    }

    return JPanel(BorderLayout()).apply {
      add(originalComponent, BorderLayout.WEST)
      add(spacer, BorderLayout.CENTER)
      add(labelComponent, BorderLayout.EAST)
    }
  }
}
