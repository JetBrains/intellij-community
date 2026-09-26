// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

internal class CellsWithSubPanelsPanel: UISandboxPanel {

  override val title: String = "Cells With Sub-Panels"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row(DevkitUiDslBundle.message("sandbox.row")) {
        textField()
          .columns(20)
      }
      row(DevkitUiDslBundle.message("sandbox.row.2")) {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .columns(20)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
      }
      row(DevkitUiDslBundle.message("sandbox.row.3")) {
        textField()
          .align(AlignX.FILL)
      }
      row(DevkitUiDslBundle.message("sandbox.row.4")) {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .align(AlignX.FILL)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
          .align(AlignX.FILL)
      }
    }
  }
}