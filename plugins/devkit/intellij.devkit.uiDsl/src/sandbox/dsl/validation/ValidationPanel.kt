// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.dsl.validation

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.intList
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class ValidationPanel : UISandboxPanel {

  override val title: String = "Validation API"

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var result: DialogPanel
    result = panel {
      lateinit var cbValidationEnabled: JCheckBox

      row {
        cbValidationEnabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.validation.enabled"))
          .selected(true)
          .component
      }

      row {
        textField()
          .comment(DevkitUiDslBundle.message("sandbox.text.must.be.not.empty"))
          .cellValidation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule(DevkitUiDslBundle.message("sandbox.dialog.message.must.be.not.empty")) { it.text.isNullOrEmpty() }
          }
      }

      row(DevkitUiDslBundle.message("sandbox.segmented.button")) {
        val segmentedButton = segmentedButton(intList(4)) { text = "Item $it" }
          .validation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule(DevkitUiDslBundle.message("sandbox.dialog.message.cannot.be.empty")) { it.selectedItem == null }
          }
        button(DevkitUiDslBundle.message("sandbox.button.reset")) {
          segmentedButton.selectedItem = null
        }
      }

      row {
        button(DevkitUiDslBundle.message("sandbox.button.validate")) {
          result.validateAll()
        }
      }
    }

    result.registerValidators(disposable)
    return result
  }
}