// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.keymapChooser

import com.intellij.ide.startup.importSettings.chooser.ui.FilledRoundedBorder
import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class KeymapPane(val keymap: Keymap) {
  private val KEY_BACKGROUND = JBColor(0xDFE1E5, 0x4E5157)

  private val names = mutableListOf<ShortcutItem>()

  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value

      update()
    }

  private val background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))

  private val activeBorder = FilledRoundedBorder({ RoundedPanel.SELECTED_BORDER_COLOR }, { background }, RoundedPanel.thickness,
                                                 RoundedPanel.RADIUS)
  private val regularBorder = FilledRoundedBorder({ RoundedPanel.BORDER_COLOR }, { background }, RoundedPanel.thickness,
                                                  RoundedPanel.RADIUS)


  private val keyMapGridBagLayout = GridBagLayout()
  val pane = JPanel().apply {
    setFocusable(true)

    layout = VerticalLayout(0)
    border = regularBorder

    add(JLabel(keymap.name).apply {
      font = JBFont.label().asBold()
      border = JBUI.Borders.empty(11, 0, 13, 0)
      horizontalAlignment = SwingConstants.CENTER
      minimumSize = Dimension(0, 0)
    })

    add(JPanel(keyMapGridBagLayout).apply {
      isOpaque = false
      minimumSize = Dimension(0, 0)
      val gbc1 = GridBagConstraints()
      gbc1.insets = JBUI.insetsBottom(14)
      gbc1.gridy = 0

      keymap.shortcut.forEach {
        gbc1.weightx = 1.0
        gbc1.weighty = 0.0
        gbc1.gridx = 0
        gbc1.anchor = GridBagConstraints.CENTER
        gbc1.fill = GridBagConstraints.HORIZONTAL
        val name = JLabel(it.name)
        add(name, gbc1)

        gbc1.gridx = 1
        gbc1.weightx = 0.0
        gbc1.weighty = 0.0
        gbc1.fill = GridBagConstraints.NONE

        val keyPanel = JPanel(VerticalLayout(0)).apply {
          isOpaque = false
          add(JLabel(it.value).apply {
            font = JBFont.medium()
            border = JBUI.Borders.empty(0, 4)
          })
          border = FilledRoundedBorder({KEY_BACKGROUND}, {KEY_BACKGROUND}, 2, 7)
        }
        border = JBUI.Borders.empty(0, 8)

        names.add(ShortcutItem(name, keyPanel))
        add(keyPanel, gbc1)
        gbc1.gridy += 1
      }
    })
  }

  init {
    update()
  }


  private fun update() {
    names.forEach {
      it.name.isVisible = active
      val constraints = keyMapGridBagLayout.getConstraints(it.value)
      constraints.anchor = if(active) GridBagConstraints.LINE_START else GridBagConstraints.CENTER
      keyMapGridBagLayout.setConstraints(it.value, constraints)
    }
    pane.border = if (active) activeBorder else regularBorder
  }

  private data class ShortcutItem(val name: JComponent, val value: JComponent)
}

data class Keymap(val id: String, val name: @NlsSafe String, val shortcut: List<ShortcutValue>)
data class ShortcutValue(val name: @NlsSafe String, val value: @NlsSafe String)