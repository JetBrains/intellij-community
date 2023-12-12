// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
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
  val panel: DialogPanel = panel {
    group("Check Same Height") {
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

    group("Baseline Align") {
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
    }

    group("Buttons States (Check Focused Manually)") {
      row {
        panel {
          row {
            checkBox("checkBox")
          }
          row {
            checkBox("checkBoxDisabled").enabled(false)
          }
          row {
            checkBox("checkBoxSelected").selected(true)
          }
          row {
            checkBox("checkBoxSelectedDisabled").selected(true).enabled(false)
          }
        }
        panel {
          row {
            threeStateCheckBox("checkBoxIndeterminate")
          }
          row {
            threeStateCheckBox("checkBoxIndeterminateDisabled").enabled(false)
          }
        }
        panel {
          buttonsGroup {
            row {
              radioButton("radioButton")
            }
            row {
              radioButton("radioButtonSelected").selected(true)
            }
          }
        }
        panel {
          buttonsGroup {
            row {
              radioButton("radioButtonDisabled").enabled(false)
            }
            row {
              radioButton("radioButtonSelectedDisabled").selected(true).enabled(false)
            }
          }
        }
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
