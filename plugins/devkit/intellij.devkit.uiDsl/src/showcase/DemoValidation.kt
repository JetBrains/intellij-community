// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.validation.Level

@Demo(title = "demo.validation.title", description = "demo.validation.description")
fun demoValidation(): DialogPanel {
  return panel {
    panel {
      row {
        textField()
          .text(DevkitUiDslBundle.message("demo.validation.digits.warning"))
          .columns(COLUMNS_MEDIUM)
          .cellValidation {
            addInputRule(DevkitUiDslBundle.message("demo.validation.no.digits.warning"), level = Level.WARNING) {
              containsDigit(it.text)
            }
          }
        textField()
          .text(DevkitUiDslBundle.message("demo.validation.digits.error"))
          .columns(COLUMNS_MEDIUM)
          .cellValidation {
            addInputRule(DevkitUiDslBundle.message("demo.validation.no.digits.error")) {
              containsDigit(it.text)
            }
          }
      }.rowComment(DevkitUiDslBundle.message("demo.validation.row.comment"))
    }
  }
}

private fun containsDigit(s: String?): Boolean {
  return s?.contains(Regex("\\d")) == true
}