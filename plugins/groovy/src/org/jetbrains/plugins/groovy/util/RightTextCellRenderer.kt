// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
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
  private val text: (T) -> @Nls String?
) : ListCellRenderer<T> {

  private val label = JBLabel().apply {
    isOpaque = true
    border = JBUI.Borders.emptyRight(UIUtil.getListCellHPadding())
  }

  private val spacer = JPanel().apply {
    border = JBUI.Borders.empty(0, 2)
  }

  private val layout = BorderLayout()

  private val rendererComponent = JPanel(layout).apply {
    add(spacer, BorderLayout.CENTER)
    add(label, BorderLayout.EAST)
  }

  override fun getListCellRendererComponent(list: JList<out T>?,
                                            value: T?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val originalComponent = baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    if (value == null || list == null || originalComponent == null) return originalComponent

    val text = text(value) ?: return originalComponent

    val bg = if (isSelected) UIUtil.getListSelectionBackground(true) else originalComponent.background

    label.text = text
    label.background = bg
    label.foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else NamedColorUtil.getInactiveTextColor()

    spacer.background = bg

    layout.getLayoutComponent(BorderLayout.WEST)?.let(rendererComponent::remove)
    rendererComponent.add(originalComponent, BorderLayout.WEST)

    return rendererComponent
  }
}
