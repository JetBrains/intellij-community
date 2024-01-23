// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.themeChooser

import com.intellij.ide.startup.importSettings.chooser.ui.FilledRoundedBorder
import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.ide.startup.importSettings.data.WizardScheme
import com.intellij.ide.startup.importSettings.wizard.keymapChooser.KeymapPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.font.TextAttribute
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class SchemePane(val scheme: WizardScheme) {
  private val RADIUS = 15
  private val thickness = 2

  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value

      update()
    }

  private val activeBorder = FilledRoundedBorder(KeymapPane.SELECTED_BORDER_COLOR, RADIUS, thickness, false)
  private val border = FilledRoundedBorder(KeymapPane.BORDER_COLOR, RADIUS, thickness, false)

  private fun update() {
    pane.border = if (active) activeBorder else border
  }

  val pane: JPanel = RoundedPanel(RADIUS).apply {
    contentPanel.apply {

      layout = GridBagLayout()

      background = scheme.backgroundColor
      preferredSize = Dimension(preferredSize.width, 0)
      minimumSize = Dimension(0, 0)

      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 0.0
      gbc.weighty = 0.0
      gbc.fill = GridBagConstraints.CENTER

      add(JLabel(scheme.name).apply {
        font = JBFont.h4().deriveFont(mapOf(
          TextAttribute.WEIGHT to TextAttribute.WEIGHT_SEMIBOLD
        ))
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
  }

  init {
    update()
  }
}