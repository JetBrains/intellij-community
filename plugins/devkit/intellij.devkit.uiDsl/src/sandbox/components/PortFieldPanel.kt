// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.PortField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

private const val SAMPLE_PORT = 8080
private const val MAX_PORT = 65535

internal class PortFieldPanel : UISandboxPanel {

  override val title: String = "PortField"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.port.field.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.port.field.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.port.field.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.port.field.label.port")) {
          cell(PortField(SAMPLE_PORT))
        }
        row(DevkitUiDslBundle.message("sandbox.port.field.label.not.set")) {
          cell(PortField())
        }
        row(DevkitUiDslBundle.message("sandbox.port.field.label.disabled")) {
          cell(PortField(SAMPLE_PORT).apply { isEnabled = false })
        }
      }

      group(DevkitUiDslBundle.message("sandbox.port.field.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.port.field.ui.dsl.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.port.field.label.port")) {
          intTextField(0..MAX_PORT)
            .text(SAMPLE_PORT.toString())
        }
      }
    }

    result.registerValidators(disposable)

    return result
  }
}
