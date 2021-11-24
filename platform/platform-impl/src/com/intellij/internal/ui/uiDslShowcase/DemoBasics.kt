// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel

@Demo(title = "Basics",
  description = "UI DSL builder builds content row by row. Every row consist of cells, last cell in row occupies all remaining width. " +
                "Rows have layout property (see RowLayout) which specify policy of cells layout in row.<br>" +
                "Result of builder is a grid like structure (see GridLayout), where near cells can be merged into one cell. " +
                "Every cell can contain some component or a sub-grid.")
fun demoBasics(): DialogPanel {
  return panel {
    row("Row1 label:") {
      textField()
      label("Some text")
    }

    row("Row2:") {
      label("This text is aligned with previous row")
    }

    row("Row3:") {
      label("Rows 3 and 4 are in common parent grid")
      textField()
    }.layout(RowLayout.PARENT_GRID)

    row("Row4:") {
      textField()
      label("Rows 3 and 4 are in common parent grid")
    }.layout(RowLayout.PARENT_GRID)
  }
}
