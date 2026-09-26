// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class WizardPagePane(centralPane: JComponent, buttons: List<JButton>, leftComponent: JComponent? = null) : JPanel() {
  init {
    layout = GridBagLayout()

    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 0.0
    gbc.fill = GridBagConstraints.HORIZONTAL
    isOpaque = false

    add(SeparatorComponent(JBColor.namedColor("Borders.color", JBColor.BLACK), SeparatorOrientation.HORIZONTAL), gbc)

    gbc.fill = GridBagConstraints.BOTH
    gbc.gridy = 1
    gbc.weighty = 1.0
    add(centralPane, gbc)

    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.gridy = 2
    gbc.weighty = 0.0
    add(SeparatorComponent(JBColor.namedColor("Borders.color", JBColor.BLACK), SeparatorOrientation.HORIZONTAL), gbc)

    val buttonPane = JPanel(BorderLayout()).apply {
      leftComponent?.let {
        add(leftComponent, BorderLayout.CENTER)
      }

      add(DialogWrapper.layoutButtonsPanel(buttons), BorderLayout.EAST)
      border = JBUI.Borders.empty(3, 0, 3, 20)
      preferredSize = Dimension(preferredSize.width, JBUI.scale(57))
    }

    gbc.gridy = 3
    gbc.anchor = GridBagConstraints.SOUTH
    gbc.fill = GridBagConstraints.HORIZONTAL
    add(buttonPane, gbc)

  }
}