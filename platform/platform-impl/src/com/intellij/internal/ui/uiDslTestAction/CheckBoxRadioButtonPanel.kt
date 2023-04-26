// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.border.Border

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class CheckBoxRadioButtonPanel {
  val panel = panel {
    row {
      panel {
        buttonsGroup {
          row {
            label("Marker")
          }
          for (i in 1..5) {
            row { checkBox("DcheckBox$i") }
          }
        }
      }.resizableColumn()
        .align(AlignY.TOP)
      panel {
        row {
          label("Marker")
        }
        buttonsGroup {
          for (i in 1..5) {
            row { radioButton("DradioButton$i") }
          }
        }
      }.resizableColumn()
        .align(AlignY.TOP)
    }

    buttonsGroup {
      row {
        checkBox("Border: 2,10,20,30")
          .customize(Color.GREEN, JBUI.Borders.customLine(Color.ORANGE, 2, 10, 20, 30))

        radioButton("Border: 10,20,30,2")
          .customize(Color.ORANGE, JBUI.Borders.customLine(Color.GREEN, 10, 20, 30, 2))

        checkBox("Border: 0,0,0,0")
          .customize(Color.GREEN, JBUI.Borders.customLine(Color.ORANGE, 0, 0, 0, 0))

        radioButton("Border: 0,0,0,0")
          .customize(Color.ORANGE, JBUI.Borders.customLine(Color.GREEN, 0, 0, 0, 0))
      }
    }

    buttonsGroup {
      row {
        val checkBox = JBCheckBox()
        cell(checkBox)
          .comment("checkBox(null), prefSize = ${checkBox.preferredSize}")

        val radioButton = JBRadioButton()
        cell(radioButton)
          .comment("radioButton(null), prefSize = ${radioButton.preferredSize}")
      }
    }

    row {
      val panel = JPanel()
      panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
      for (i in 1..5) {
        panel.add(JCheckBox("BoxLayout$i"))
      }
      cell(panel)
    }

    buttonsGroup {
      row {
        checkBox("Base line check")
        comment("Some comment")
      }
      row {
        radioButton("Base line check")
        comment("Some small comment")
          .applyToComponent { font = font.deriveFont(font.size - 2.0f) }
      }
    }
  }
}

private fun Cell<JToggleButton>.customize(background: Color, border: Border) {
  applyToComponent {
    isOpaque = true
    this.background = background
    this.border = border
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
  }
}
