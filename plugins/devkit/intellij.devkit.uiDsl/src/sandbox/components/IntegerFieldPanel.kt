// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

private const val MIN_TIMEOUT = 1
private const val MAX_TIMEOUT = 3600
private const val DEFAULT_TIMEOUT = 30
private const val FIELD_COLUMNS = 6

internal class IntegerFieldPanel : UISandboxPanel {

  override val title: String = "IntegerField"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.integer.field.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.integer.field.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.integer.field.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.integer.field.label.range")) {
          cell(createIntegerField())
        }
        row(DevkitUiDslBundle.message("sandbox.integer.field.label.can.be.empty")) {
          cell(createIntegerField().apply {
            isCanBeEmpty = true
            text = ""
          })
        }
        row(DevkitUiDslBundle.message("sandbox.integer.field.label.disabled")) {
          cell(createIntegerField().apply { isEnabled = false })
        }
      }

      group(DevkitUiDslBundle.message("sandbox.integer.field.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.integer.field.ui.dsl.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.integer.field.label.range")) {
          intTextField(MIN_TIMEOUT..MAX_TIMEOUT)
            .text(DEFAULT_TIMEOUT.toString())
        }
      }
    }

    result.registerValidators(disposable)

    return result
  }

  private fun createIntegerField(): IntegerField {
    return IntegerField(DevkitUiDslBundle.message("sandbox.integer.field.value.name"), MIN_TIMEOUT, MAX_TIMEOUT).apply {
      value = DEFAULT_TIMEOUT
      columns = FIELD_COLUMNS
    }
  }
}
