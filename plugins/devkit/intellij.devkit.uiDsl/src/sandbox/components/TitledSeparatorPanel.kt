// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.TitledSeparator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class TitledSeparatorPanel : UISandboxPanel {

  override val title: String = "TitledSeparator"

  override fun createContent(disposable: Disposable): JComponent {
    val text = DevkitUiDslBundle.message("sandbox.titled.separator.text")

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.titled.separator.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.titled.separator.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.titled.separator.label.default")) {
          cell(TitledSeparator(text)).align(AlignX.FILL)
        }
        row(DevkitUiDslBundle.message("sandbox.titled.separator.label.disabled")) {
          cell(TitledSeparator(text).apply { isEnabled = false }).align(AlignX.FILL)
        }
        row(DevkitUiDslBundle.message("sandbox.titled.separator.label.no.title")) {
          cell(TitledSeparator()).align(AlignX.FILL)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.titled.separator.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.titled.separator.ui.dsl.note"))
        }
        group(text) {
          row {
            checkBox(DevkitUiDslBundle.message("sandbox.titled.separator.check.box"))
          }
        }
        separator()
        row {
          checkBox(DevkitUiDslBundle.message("sandbox.titled.separator.check.box"))
        }
      }
    }
  }
}
