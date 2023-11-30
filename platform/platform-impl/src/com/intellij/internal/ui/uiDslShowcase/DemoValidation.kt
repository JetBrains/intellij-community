// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.validation.Level

@Suppress("DialogTitleCapitalization")
@Demo(title = "Validation",
      description = "It's possible to use validation")
fun demoValidation(): DialogPanel {
  return panel {
    panel {
      row {
        textField()
          .text("Digits are marked as warning")
          .columns(COLUMNS_MEDIUM)
          .cellValidation {
            addInputRule("Shouldn't contain digits", level = Level.WARNING) {
              containsDigit(it.text)
            }
          }
        textField()
          .text("Digits are marked as error")
          .columns(COLUMNS_MEDIUM)
          .cellValidation {
            addInputRule("Mustn't contain digits") {
              containsDigit(it.text)
            }
          }
      }.rowComment("There are two kinds of validation: warning and error")
    }
  }
}

private fun containsDigit(s: String?): Boolean {
  return s?.contains(Regex("\\d")) == true
}