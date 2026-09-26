// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SimpleCustomDecorationPath(@JvmField val frame: JFrame, private val isGrey: () -> Boolean = { false }) {
  internal class SimpleCustomDecorationPathComponent(frame: JFrame, isGrey: () -> Boolean = { false }) : JPanel() {
    private val manager = SimpleCustomDecorationPath(frame, isGrey)

    init {
      layout = GridLayout()
      RowsGridBuilder(this).row(resizable = true).cell(component = manager.label,
                                                       verticalAlign = VerticalAlign.CENTER,
                                                       horizontalAlign = HorizontalAlign.FILL,
                                                       resizableColumn = true)

      manager.updateLabelForeground()
    }

    override fun addNotify() {
      super.addNotify()
      manager.frame.addPropertyChangeListener("title", manager.frameTitleListener)
      updateTitle()
    }

    override fun updateUI() {
      super.updateUI()
      if (parent != null) {
        manager.updateLabelForeground()
      }
    }

    override fun removeNotify() {
      manager.onRemove()
      super.removeNotify()
    }

    private fun updateTitle() {
      manager.updateTitle()
    }

    fun updateBorders(rightGap: Int) {
      border = JBUI.Borders.empty(2, 0, 0, rightGap)
    }

    fun updateLabelForeground() {
      manager.updateLabelForeground()
    }
  }

  private val frameTitleListener = PropertyChangeListener { updateTitle() }
  private val label = JBLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    font = JBFont.create(font, false)
  }

  private val insets = JBUI.insetsTop(2)

  init {
    updateLabelForeground()
  }

  fun add(panel: JPanel, rightGap: Int) {
    frame.addPropertyChangeListener("title", frameTitleListener)
    updateTitle()
    insets.right = rightGap
    panel.add(label, GridBagConstraints().also {
      it.gridx = 0
      it.gridy = 0
      it.fill = GridBagConstraints.CENTER
      it.insets = insets
    })
  }

  fun onRemove() {
    frame.removePropertyChangeListener("title", frameTitleListener)
  }

  private fun updateTitle() {
    label.text = frame.title
  }

  fun updateBorders(left: Int, right: Int) {
    insets.left = left
    insets.right = right
  }

  fun updateLabelForeground() {
    label.foreground = if (isGrey.invoke()) {
      JBUI.CurrentTheme.Popup.headerForeground(false)
    }
    else {
      @Suppress("UnregisteredNamedColor")
      JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
    }
  }
}