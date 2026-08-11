// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class LabelsPanel : UISandboxPanel {

  override val title: String = "Labels"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row(DevkitUiDslBundle.message("sandbox.row1")) {
        textField()
      }

      group("RadioButton") {
        buttonsGroup {
          row(DevkitUiDslBundle.message("sandbox.row1")) {
            radioButton("radioButton")
          }
        }
        row(DevkitUiDslBundle.message("sandbox.row2")) {
          textField()
        }
      }

      group("CheckBox") {
        row(DevkitUiDslBundle.message("sandbox.row1")) {
          checkBox("checkBox")
        }
        row(DevkitUiDslBundle.message("sandbox.row2")) {
          textField()
        }
      }

      group("CheckBox") {
        row(DevkitUiDslBundle.message("sandbox.row1")) {
          checkBox("checkBox")
        }
        row(DevkitUiDslBundle.message("sandbox.row2.long.label")) {
          textField()
        }
      }

      @Suppress("HardCodedStringLiteral")
      group("layout(RowLayout.INDEPENDENT)") {
        row(DevkitUiDslBundle.message("sandbox.row1")) {
          checkBox("checkBox")
        }.layout(RowLayout.INDEPENDENT)
        row(DevkitUiDslBundle.message("sandbox.row2")) {
          textField()
        }.layout(RowLayout.INDEPENDENT)
      }

      group("Cell.label") {
        row {
          checkBox("checkBox")
            .label(DevkitUiDslBundle.message("sandbox.label.row1"))
        }
        row {
          textField()
            .label(DevkitUiDslBundle.message("sandbox.label.row2"))
        }
      }
    }
  }
}