// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.component.labels.title", description = "demo.component.labels.description")
fun demoComponentLabels(): DialogPanel {
  return panel {
    row(DevkitUiDslBundle.message("demo.component.labels.row.label")) {
      textField()
      textField()
        .label(DevkitUiDslBundle.message("demo.component.labels.cell.label.left"))
    }

    row {
      textField()
        .label(DevkitUiDslBundle.message("demo.component.labels.cell.label.top"), LabelPosition.TOP)
    }

    group(DevkitUiDslBundle.message("demo.component.labels.group.checkbox")) {
      row(DevkitUiDslBundle.message("demo.component.labels.row1")) {
        checkBox(DevkitUiDslBundle.message("demo.component.labels.checkbox"))
      }
      row(DevkitUiDslBundle.message("demo.component.labels.row2")) {
        textField()
      }
      row {
        comment(DevkitUiDslBundle.message("demo.component.labels.guidelines.comment"))
      }
    }
  }
}