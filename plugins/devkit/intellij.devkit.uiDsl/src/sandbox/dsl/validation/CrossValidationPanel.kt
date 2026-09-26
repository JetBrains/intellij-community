// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.dsl.validation

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class CrossValidationPanel : UISandboxPanel {

  override val title: String = "Cross Validation"

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var result: DialogPanel
    val allValidators = mutableListOf<ComponentValidator>()
    result = panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.label.kotlin.ui.dsl.doesn.t.have.special.api.for.cross"))
      }

      val allTextFields = mutableListOf<JBTextField>()
      for (i in 1..3) {
        row(DevkitUiDslBundle.message("sandbox.field.0", i)) {
          val textField = textField()
            .onChanged {
              for (validator in allValidators) {
                validator.revalidate()
              }
            }.component
          val validator = ComponentValidator(disposable)
            .withValidator { validate(textField, allTextFields) }
          validator.installOn(textField)
          allValidators.add(validator)
          allTextFields.add(textField)
        }
      }
    }

    for (validator in allValidators) {
      validator.revalidate()
    }
    return result
  }

  private fun validate(field: JBTextField, allFields: List<JBTextField>): ValidationInfo? {
    val sameFieldsIndexes = allFields.mapIndexedNotNull { index, textField ->
      if (field !== textField && field.text == textField.text) index + 1 else null
    }

    return when (sameFieldsIndexes.size) {
      0 -> null
      1 -> ValidationInfo(DevkitUiDslBundle.message("sandbox.dialog.message.same.as.field", sameFieldsIndexes[0]), field)
      else -> {
        val indexes = sameFieldsIndexes.joinToString(separator = " and ") { it.toString() }
        ValidationInfo(DevkitUiDslBundle.message("sandbox.dialog.message.same.as.fields", indexes), field)
      }
    }
  }
}