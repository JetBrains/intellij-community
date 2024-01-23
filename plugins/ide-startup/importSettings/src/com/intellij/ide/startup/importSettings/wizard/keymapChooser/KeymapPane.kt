// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.keymapChooser

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.font.TextAttribute
import javax.swing.JLabel

class KeymapPane(val keymap: Keymap) {
  companion object {
    val SELECTED_BORDER_COLOR = JBColor(0x3574F0, 0x3574F0)
    val BORDER_COLOR = JBColor(0xD3D5DB, 0x43454A)

  }

  private val names = mutableListOf<JLabel>()

  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value

      update()
    }


  private fun update() {
    names.forEach { it.isVisible = active }
    pane.border = RoundedLineBorder(if (active) SELECTED_BORDER_COLOR else BORDER_COLOR)
    pane.background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))
  }

  val pane = panel {
    row {
      label(keymap.name).align(AlignX.CENTER).customize(UnscaledGaps(10, 3, 10, 3)).applyToComponent {
        font = JBUI.Fonts.label().deriveFont(mapOf(
          TextAttribute.WEIGHT to TextAttribute.WEIGHT_SEMIBOLD
        ))
      }
    }
    panel {
      keymap.shortcut.forEach {
        row {
          names.add(label(it.name).component)
          label(it.value)
        }.layout(RowLayout.PARENT_GRID)
      }
    }.align(AlignX.CENTER)
  }

  /*  val pane_ = JPanel(BorderLayout()).apply {
      add(panel, BorderLayout.CENTER)
    }*/
  init {
    update()
  }

}

data class Keymap(val id: String, val name: @NlsSafe String, val shortcut: List<ShortcutValue>)
data class ShortcutValue(val name: @NlsSafe String, val value: @NlsSafe String)