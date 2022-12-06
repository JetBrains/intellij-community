// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingConstants

class MacCustomDecorationPath(val frame: JFrame): JPanel() {

  private val frameTitleListener = PropertyChangeListener { updateTitle() }
  private val label = JBLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    foreground = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
  }

  init {
    layout = GridLayout()
    RowsGridBuilder(this).row(resizable = true).cell(component = label,
                                                     verticalAlign = VerticalAlign.CENTER,
                                                     horizontalAlign = HorizontalAlign.FILL,
                                                     resizableColumn = true)
  }

  override fun addNotify() {
    super.addNotify()
    frame.addPropertyChangeListener("title", frameTitleListener)
    updateTitle()
  }

  override fun removeNotify() {
    frame.removePropertyChangeListener(frameTitleListener)
    super.removeNotify()
  }

  private fun updateTitle() {
    label.text = frame.title
  }
}