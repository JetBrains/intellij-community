// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.border.Border

@NlsSafe private const val BORDER_1 = "Border: 2,10,20,30"
@NlsSafe private const val BORDER_2 = "Border: 10,20,30,2"
@NlsSafe private const val BORDER_3 = "Border: 0,0,0,0"
@NlsSafe private const val BORDER_4 = "Border: 0,0,0,0"

@Suppress("UseJBColor")
internal class CheckBoxRadioButtonPanel : UISandboxPanel {

  override val title: String = "CheckBox/RadioButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.check.same.height")) {
        buttonsGroup {
          row {
            panel {
              for (i in 1..5) {
                row { checkBox("CheckBox$i") }
              }
            }.align(AlignY.TOP)
            panel {
              for (i in 1..5) {
                row { radioButton("RadioButton$i") }
              }
            }.align(AlignY.TOP)
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.baseline.align")) {
        buttonsGroup {
          row {
            checkBox(BORDER_1)
              .customize(Color.GREEN, JBUI.Borders.customLine(Color.ORANGE, 2, 10, 20, 30))

            radioButton(BORDER_2)
              .customize(Color.ORANGE, JBUI.Borders.customLine(Color.GREEN, 10, 20, 30, 2))

            checkBox(BORDER_3)
              .customize(Color.GREEN, JBUI.Borders.customLine(Color.ORANGE, 0, 0, 0, 0))

            radioButton(BORDER_4)
              .customize(Color.ORANGE, JBUI.Borders.customLine(Color.GREEN, 0, 0, 0, 0))
          }
        }
      }

      buttonsGroup {
        row {
          val checkBox = JBCheckBox()
          @Suppress("HardCodedStringLiteral")
          cell(checkBox)
            .comment("checkBox(null), prefSize = ${checkBox.preferredSize}")

          val radioButton = JBRadioButton()
          @Suppress("HardCodedStringLiteral")
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
          checkBox(DevkitUiDslBundle.message("sandbox.checkbox.base.line.check"))
          comment(DevkitUiDslBundle.message("sandbox.text.some.comment"))
        }
        row {
          radioButton(DevkitUiDslBundle.message("sandbox.radio.base.line.check"))
          comment(DevkitUiDslBundle.message("sandbox.text.some.small.comment"))
            .applyToComponent { font = font.deriveFont(font.size - 2.0f) }
        }
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
