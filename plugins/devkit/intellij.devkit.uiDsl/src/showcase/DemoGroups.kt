// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel

@Suppress("DialogTitleCapitalization")
@Demo(title = "demo.groups.title",
      description = "demo.groups.description",
      scrollbar = true)
fun demoGroups(): DialogPanel {
  return panel {
    panel {
      row(DevkitUiDslBundle.message("demo.groups.panel.row")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.groups.panel.comment"))
    }

    rowsRange {
      row(DevkitUiDslBundle.message("demo.groups.rows.range.row")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.groups.rows.range.comment"))
    }

    group(DevkitUiDslBundle.message("demo.groups.group.title")) {
      row(DevkitUiDslBundle.message("demo.groups.group.row")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.groups.group.comment"))
    }

    groupRowsRange(DevkitUiDslBundle.message("demo.groups.group.rows.range.title")) {
      row(DevkitUiDslBundle.message("demo.groups.group.rows.range.row")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.groups.group.rows.range.comment"))
    }

    collapsibleGroup(DevkitUiDslBundle.message("demo.groups.collapsible.title")) {
      row(DevkitUiDslBundle.message("demo.groups.collapsible.row")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.groups.collapsible.comment"))
    }

    var value = true
    buttonsGroup(DevkitUiDslBundle.message("demo.groups.button.group.title")) {
      row {
        radioButton(DevkitUiDslBundle.message("demo.groups.true"), true)
      }
      row {
        radioButton(DevkitUiDslBundle.message("demo.groups.false"), false)
      }.rowComment(DevkitUiDslBundle.message("demo.groups.button.group.comment"))
    }.bind({ value }, { value = it })

    separator()
      .rowComment(DevkitUiDslBundle.message("demo.groups.separator.comment"))

    row {
      label(DevkitUiDslBundle.message("demo.groups.row.panel.label"))
      panel {
        row(DevkitUiDslBundle.message("demo.groups.sub.panel.row1")) {
          textField()
        }
        row(DevkitUiDslBundle.message("demo.groups.sub.panel.row2")) {
          textField()
        }
      }
    }
  }
}
