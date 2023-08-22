// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected

@Demo(title = "Enabled/Visible",
  description = "Visibility and enabled state of panels, rows and cells can be bound to another components and applied automatically")
fun demoAvailability(): DialogPanel {
  return panel {
    group("Enabled") {
      lateinit var checkBox: Cell<JBCheckBox>
      row {
        checkBox = checkBox("Check to enable options")
      }
      indent {
        row {
          checkBox("Option 1")
        }
        row {
          checkBox("Option 2")
        }
      }.enabledIf(checkBox.selected)
      row {
        val mailCheckBox = checkBox("Use mail:")
          .gap(RightGap.SMALL)
        textField()
          .enabledIf(mailCheckBox.selected)
      }
    }

    group("Visible") {
      lateinit var checkBox: Cell<JBCheckBox>
      row {
        checkBox = checkBox("Check to show options")
      }
      indent {
        row {
          checkBox("Option 1")
        }
        row {
          checkBox("Option 2")
        }
      }.visibleIf(checkBox.selected)
    }
  }
}
