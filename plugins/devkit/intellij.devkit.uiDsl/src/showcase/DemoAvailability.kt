// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected

@Demo(title = "demo.availability.title", description = "demo.availability.description")
fun demoAvailability(): DialogPanel {
  return panel {
    group(DevkitUiDslBundle.message("demo.availability.group.enabled")) {
      lateinit var checkBox: Cell<JBCheckBox>
      row {
        checkBox = checkBox(DevkitUiDslBundle.message("demo.availability.check.to.enable"))
      }
      indent {
        row {
          checkBox(DevkitUiDslBundle.message("demo.availability.option1"))
        }
        row {
          checkBox(DevkitUiDslBundle.message("demo.availability.option2"))
        }
      }.enabledIf(checkBox.selected)
      row {
        val mailCheckBox = checkBox(DevkitUiDslBundle.message("demo.availability.use.mail"))
          .gap(RightGap.SMALL)
        textField()
          .enabledIf(mailCheckBox.selected)
      }
    }

    group(DevkitUiDslBundle.message("demo.availability.group.visible")) {
      lateinit var checkBox: Cell<JBCheckBox>
      row {
        checkBox = checkBox(DevkitUiDslBundle.message("demo.availability.check.to.show"))
      }
      indent {
        row {
          checkBox(DevkitUiDslBundle.message("demo.availability.option1"))
        }
        row {
          checkBox(DevkitUiDslBundle.message("demo.availability.option2"))
        }
      }.visibleIf(checkBox.selected)
    }
  }
}
