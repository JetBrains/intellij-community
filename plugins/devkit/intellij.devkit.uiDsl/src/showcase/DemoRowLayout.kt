// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "demo.row.layout.title", description = "demo.row.layout.description")
fun demoRowLayout(): DialogPanel {
  return panel {
    row(DevkitUiDslBundle.message("demo.row.layout.parent.grid.0")) {
      label(DevkitUiDslBundle.message("demo.row.layout.label1"))
      label(DevkitUiDslBundle.message("demo.row.layout.label2"))
    }.layout(RowLayout.PARENT_GRID)

    row(DevkitUiDslBundle.message("demo.row.layout.parent.grid")) {
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field1"))
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field2"))
    }.layout(RowLayout.PARENT_GRID)

    row(DevkitUiDslBundle.message("demo.row.layout.label.aligned")) {
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field1"))
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field2"))
    }

    row {
      label(DevkitUiDslBundle.message("demo.row.layout.independent"))
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field1"))
      textField().text(DevkitUiDslBundle.message("demo.row.layout.text.field2"))
    }
  }
}
