// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
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
      row("Row") {
        textField()
          .columns(20)
      }
      row("Row 2") {
        val subPanel = com.intellij.ui.dsl.builder.panel {
          row {
            textField()
              .columns(20)
              .text("Sub-Paneled Row")
          }
        }
        cell(subPanel)
      }
      row("Row 3") {
        textField()
          .align(AlignX.FILL)
      }
      row("Row 4") {
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