// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel

@Demo(title = "demo.basics.title", description = "demo.basics.description")
fun demoBasics(): DialogPanel {
  return panel {
    row(DevkitUiDslBundle.message("demo.basics.row1.label")) {
      textField()
      label(DevkitUiDslBundle.message("demo.basics.row1.text"))
    }

    row(DevkitUiDslBundle.message("demo.basics.row2.label")) {
      label(DevkitUiDslBundle.message("demo.basics.row2.text"))
    }

    row(DevkitUiDslBundle.message("demo.basics.row3.label")) {
      label(DevkitUiDslBundle.message("demo.basics.rows.parent.grid"))
      textField()
    }.layout(RowLayout.PARENT_GRID)

    row(DevkitUiDslBundle.message("demo.basics.row4.label")) {
      textField()
      label(DevkitUiDslBundle.message("demo.basics.rows.parent.grid"))
    }.layout(RowLayout.PARENT_GRID)
  }
}
