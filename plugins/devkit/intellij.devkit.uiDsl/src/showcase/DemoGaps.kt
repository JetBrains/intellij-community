// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.gaps.title",
      description = "demo.gaps.description",
      scrollbar = true)
fun demoGaps(): DialogPanel {
  return panel {
    group(DevkitUiDslBundle.message("demo.gaps.group.right.small")) {
      row {
        val checkBox = checkBox(DevkitUiDslBundle.message("demo.gaps.use.mail"))
          .gap(RightGap.SMALL)
        textField()
          .enabledIf(checkBox.selected)
      }.rowComment(DevkitUiDslBundle.message("demo.gaps.checkbox.label.comment"))
      row(DevkitUiDslBundle.message("demo.gaps.width")) {
        textField()
          .gap(RightGap.SMALL)
        label(DevkitUiDslBundle.message("demo.gaps.pixels"))
      }.rowComment(DevkitUiDslBundle.message("demo.gaps.related.comment"))
    }

    group(DevkitUiDslBundle.message("demo.gaps.group.indent")) {
      row {
        label(DevkitUiDslBundle.message("demo.gaps.not.indented"))
      }
      indent {
        row {
          label(DevkitUiDslBundle.message("demo.gaps.indented"))
        }
      }
    }

    group(DevkitUiDslBundle.message("demo.gaps.group.two.columns")) {
      twoColumnsRow({
        checkBox(DevkitUiDslBundle.message("demo.gaps.first.column"))
      }, {
        checkBox(DevkitUiDslBundle.message("demo.gaps.second.column"))
      }).rowComment(DevkitUiDslBundle.message("demo.gaps.two.columns.row.comment"))
      row {
        checkBox(DevkitUiDslBundle.message("demo.gaps.column1"))
          .gap(RightGap.COLUMNS)
        checkBox(DevkitUiDslBundle.message("demo.gaps.column2"))
      }.layout(RowLayout.PARENT_GRID)
        .rowComment(DevkitUiDslBundle.message("demo.gaps.right.gap.columns.comment"))
    }

    group(DevkitUiDslBundle.message("demo.gaps.group.vertical")) {
      row {
        checkBox(DevkitUiDslBundle.message("demo.gaps.option1"))
      }
      row {
        checkBox(DevkitUiDslBundle.message("demo.gaps.option2"))
      }
      row {
        checkBox(DevkitUiDslBundle.message("demo.gaps.option.unrelated"))
      }.topGap(TopGap.MEDIUM)
    }
  }
}
