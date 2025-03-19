// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.keymapChooser

import com.intellij.ide.startup.importSettings.chooser.ui.RoundedBorder
import com.intellij.ide.startup.importSettings.chooser.ui.RoundedPanel
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class KeymapPane(val keymap: Keymap) {
  private val KEY_BACKGROUND = JBColor(0xDFE1E5, 0x4E5157)

  private val names = mutableListOf<ShortcutItem>()

  private var hover: Boolean = false

  val jRadioButton = object : JRadioButton() {
    override fun paintComponent(g: Graphics?) {}

    init {
      minimumSize = Dimension(0, 0)
      preferredSize = minimumSize
      maximumSize = minimumSize
    }
  }

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

  private val backgroundColor = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))

  private val activeBorder = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.SELECTED_BORDER_COLOR,
                                           RoundedPanel.RADIUS)

  private val regularBorder = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.THICKNESS, RoundedPanel.BORDER_COLOR,
                                            RoundedPanel.RADIUS)

  private val hoverBorder = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.THICKNESS, RoundedPanel.SELECTED_BORDER_COLOR,
                                            RoundedPanel.RADIUS)


  val mainPanel = RoundedPanel.createRoundedPane()

  private val keyMapGridBagLayout = GridBagLayout()
  val pane = JPanel(BorderLayout()).apply {
    add(mainPanel.apply {
      contentPanel.apply {
        background = backgroundColor
        layout = VerticalLayout(0)
        border = regularBorder

        add(JLabel(keymap.name).apply {
          font = JBFont.label().asBold()
          border = JBUI.Borders.empty(13, 0)
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
              add(object : JLabel(it.value) {
                override fun paintComponent(g: Graphics?) {
                  super.paintComponent(g)

                  val fm = getFontMetrics(font)
                  if (g !is Graphics2D) {
                    return
                  }
                  UISettings.setupAntialiasing(g)

                  val textWidth = fm.stringWidth(text)
                  var availableWidth = width - insets.right - insets.left
                  icon?.let {
                    availableWidth -= iconTextGap + it.iconWidth
                  }

                  toolTipText = if (textWidth > availableWidth) {
                    text
                  }
                  else null
                }

              }.apply {
                font = JBFont.medium()
                border = JBUI.Borders.empty(0, 4)
                maximumSize = Dimension(JBUI.scale(85), maximumSize.height)
              })
              border = RoundedBorder(RoundedPanel.ACTIVE_THICKNESS, RoundedPanel.ACTIVE_THICKNESS, KEY_BACKGROUND, 7)
            }
            border = JBUI.Borders.empty(0, 8)

            names.add(ShortcutItem(name, keyPanel))
            add(keyPanel, gbc1)
            gbc1.gridy += 1

          }
        })
      }
    }, BorderLayout.CENTER)
    add(jRadioButton, BorderLayout.SOUTH)
  }

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


  private fun update() {
    names.forEach {
      it.name.isVisible = active
      val constraints = keyMapGridBagLayout.getConstraints(it.value)
      constraints.anchor = if (active) GridBagConstraints.LINE_START else GridBagConstraints.CENTER
      keyMapGridBagLayout.setConstraints(it.value, constraints)
      mainPanel.border = if (active) activeBorder else if(hover) hoverBorder else regularBorder
    }

    jRadioButton.isSelected = active
  }


  private data class ShortcutItem(val name: JComponent, val value: JComponent)
}

data class Keymap(val id: String, val name: @NlsSafe String, val shortcut: List<ShortcutValue>)
data class ShortcutValue(val name: @NlsSafe String, val value: @NlsSafe String)