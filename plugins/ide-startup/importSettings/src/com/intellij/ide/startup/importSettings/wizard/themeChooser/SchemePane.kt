// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.themeChooser

import com.intellij.ide.startup.importSettings.chooser.ui.RoundedBorder
import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.ide.startup.importSettings.data.WizardScheme
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class SchemePane(val scheme: WizardScheme) {
  private val backgroundColor = scheme.backgroundColor

  private var hover: Boolean = false

  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value

      if (value) {
        SwingUtilities.invokeLater {
          jRadioButton.requestFocus()
        }
      }

      update()
    }

  private fun update() {
    pane.border = if (active) activeBorder else if(hover) hoverBorder else border
    jRadioButton.isSelected = active
  }

  val jRadioButton = object : JRadioButton() {
    init {
      preferredSize = Dimension(0, 0)
      minimumSize = preferredSize
      maximumSize = preferredSize
    }

    override fun paintComponent(g: Graphics?) {}
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
        border = JBUI.Borders.empty(9, 0, 5, 0)
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
        add(jRadioButton)
       // isOpaque = false
        border = JBUI.Borders.empty(0, 2, 2, 2)
        background = backgroundColor
      }, gbc)

    }
  }

  val pane: JPanel = roundedPanel

  private val activeBorder = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.SELECTED_BORDER_COLOR,
                                           RoundedPanel.RADIUS)

  private val hoverBorder = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.THICKNESS, RoundedPanel.SELECTED_BORDER_COLOR,
                                           RoundedPanel.RADIUS)

  private val border = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.THICKNESS, RoundedPanel.BORDER_COLOR,
                                     RoundedPanel.RADIUS)


  init {
    this.pane.addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        hover = true
        update()
      }

      override fun mouseExited(e: MouseEvent?) {
        hover = false
        update()
      }
    })
    update()
  }
}