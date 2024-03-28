// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.themeChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.ide.startup.importSettings.data.WizardScheme
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.LineBorder

class SchemePreview(scheme: WizardScheme) {

  val jLabel = JLabel(scheme.previewIcon ?: scheme.icon)

  var scheme: WizardScheme = scheme
    set(value) {
      if(field == value) return

      field = value
      jLabel.icon = value.previewIcon ?: value.icon
      pane.revalidate()
      pane.repaint()
    }

  val pane = JPanel(GridBagLayout()).apply {
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 0.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL

    add(JLabel(ImportSettingsBundle.message("theme.page.preview").apply {
      border = JBUI.Borders.empty(14, 0, 9 ,0)
    }), gbc)
    gbc.gridy = 1
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    gbc.anchor = GridBagConstraints.LINE_START
    gbc.fill = GridBagConstraints.BOTH
    add(JPanel(VerticalLayout(0)).apply {
      minimumSize = JBDimension(0, 0)
      add(jLabel.apply {
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
      })
      border = LineBorder(RoundedPanel.BORDER_COLOR)/*RoundedBorder(RoundedPanel.THICKNESS, RoundedPanel.THICKNESS, RoundedPanel.BORDER_COLOR,
                             { scheme.backgroundColor }, 10)*/
    }, gbc)
  }
}