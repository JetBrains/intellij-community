// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.themeChooser

import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.ide.startup.importSettings.data.WizardScheme
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class SchemePane(val scheme: WizardScheme) {
  private val backgroundColor = scheme.backgroundColor

  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value

      update()
    }

  private fun update() {
    pane.border = if (active) activeBorder else border
  }

  private val roundedPanel = RoundedPanel.createRoundedPane().apply {
    contentPanel.apply {

      layout = GridBagLayout()

      background = backgroundColor
      preferredSize = Dimension(preferredSize.width, 0)
      minimumSize = Dimension(0, 0)

      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 0.0
      gbc.weighty = 0.0
      gbc.fill = GridBagConstraints.CENTER

      add(JLabel(scheme.name).apply {
        font = JBFont.label().asBold()
        border = JBUI.Borders.empty(10, 0)
      }, gbc)

      gbc.gridy = 1
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.anchor = GridBagConstraints.LINE_START
      gbc.fill = GridBagConstraints.BOTH
      add(JPanel(VerticalLayout(0)).apply {
        minimumSize = JBDimension(0, 0)
        add(JLabel(scheme.icon).apply {
          horizontalAlignment = SwingConstants.LEFT
          verticalAlignment = SwingConstants.TOP
        })

      }, gbc)
    }
  }.apply {
    isFocusable = true
  }

  val pane: JPanel = roundedPanel

  private val activeBorder = roundedPanel.createBorder(RoundedPanel.SELECTED_BORDER_COLOR)
  private val border = roundedPanel.createBorder(RoundedPanel.BORDER_COLOR)


  init {
    update()
  }
}