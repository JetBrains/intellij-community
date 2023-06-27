// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SimpleCustomDecorationPath(@JvmField val frame: JFrame, private val isGrey: Boolean = false): JPanel(), UISettingsListener {
  private val frameTitleListener = PropertyChangeListener { updateTitle() }
  private val label = JBLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
  }

  val expectedHeight: Int
    get() = JBUIScale.scale(30)

  init {
    layout = GridLayout()
    RowsGridBuilder(this).row(resizable = true).cell(component = label,
                                                     verticalAlign = VerticalAlign.CENTER,
                                                     horizontalAlign = HorizontalAlign.FILL,
                                                     resizableColumn = true)

    updateMinimumSize()
    updateLabelForeground()
  }

  override fun addNotify() {
    super.addNotify()
    frame.addPropertyChangeListener("title", frameTitleListener)
    updateTitle()
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateMinimumSize()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) {
      updateMinimumSize()
      updateLabelForeground()
    }
  }

  override fun removeNotify() {
    frame.removePropertyChangeListener("title", frameTitleListener)
    super.removeNotify()
  }

  private fun updateTitle() {
    label.text = frame.title
  }

  fun updateBorders(rightGap: Int) {
    border = JBUI.Borders.empty(2, 0, 0, rightGap)
  }

  private fun updateMinimumSize() {
    minimumSize = Dimension(0, expectedHeight)
  }

  private fun updateLabelForeground() {
    label.foreground = if (isGrey) {
      JBUI.CurrentTheme.Popup.headerForeground(false)
    }
    else {
      JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
    }
  }
}