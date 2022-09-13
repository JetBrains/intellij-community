// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list

import com.intellij.navigation.TargetPresentation
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.util.function.Function
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

internal class TargetPresentationRenderer<T>(
  private val presentationProvider: Function<in T, out TargetPresentation>,
) : ListCellRenderer<T> {

  private val myComponent = JPanel(BorderLayout())
  private val myMainRenderer = TargetPresentationMainRenderer(presentationProvider)
  private val mySpacerComponent = JPanel().apply {
    border = JBUI.Borders.empty(0, 2)
  }
  private val myLocationComponent = JBLabel().apply {
    border = JBUI.Borders.emptyRight(UIUtil.getListCellHPadding())
    horizontalTextPosition = SwingConstants.LEFT
    horizontalAlignment = SwingConstants.RIGHT // align icon to the right
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val mainComponent = myMainRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    val presentation = presentationProvider.apply(value)
    val locationText = presentation.locationText
    if (locationText == null) {
      return mainComponent
    }

    myComponent.removeAll()

    val background = mainComponent.background
    myComponent.background = background
    mySpacerComponent.background = background
    myLocationComponent.background = background

    myLocationComponent.text = locationText
    myLocationComponent.icon = presentation.locationIcon
    myLocationComponent.foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(cellHasFocus) else NamedColorUtil.getInactiveTextColor()

    myComponent.add(mainComponent, BorderLayout.WEST)
    myComponent.add(mySpacerComponent, BorderLayout.CENTER)
    myComponent.add(myLocationComponent, BorderLayout.EAST)

    myComponent.accessibleContext.accessibleName = listOfNotNull(
      mainComponent.accessibleContext?.accessibleName,
      myLocationComponent.accessibleContext?.accessibleName
    ).joinToString(separator = " ")

    return myComponent
  }
}
