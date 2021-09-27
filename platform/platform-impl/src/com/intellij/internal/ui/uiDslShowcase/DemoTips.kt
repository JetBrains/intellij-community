// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign

@Suppress("DialogTitleCapitalization")
@Demo(title = "Tips",
  description = "Here are some useful tips and tricks")
fun demoTips(): DialogPanel {
  return panel {
    row {
      label("Bold text")
        .bold()
    }.rowComment("Use Cell.bold method for bold text, works for any component")

    row {
      textField()
        .columns(COLUMNS_SHORT)
    }.rowComment("Configure width of textField, comboBox and textArea by columns method")

    row("intTextField(0..1000, 100):") {
      intTextField(0..1000, 100)
        .text("500")
    }.rowComment("Use Row.intTextField for input integer numbers. There is value validation, range validation and " +
                 "supported up/down keys")

    group("Use Row.cell() to disable expanding components on whole width") {
      row("Row 1:") {
        textField()
          .gap(RightGap.SMALL)
          .resizableColumn()
          .horizontalAlign(HorizontalAlign.FILL)
        val action = object : DumbAwareAction(AllIcons.Actions.QuickfixOffBulb) {
          override fun actionPerformed(e: AnActionEvent) {
          }
        }
        actionButton(action)
      }.layout(RowLayout.PARENT_GRID)

      row("Row 2:") {
        textField()
          .gap(RightGap.SMALL)
          .horizontalAlign(HorizontalAlign.FILL)
        cell()
      }.layout(RowLayout.PARENT_GRID)
        .rowComment("Last textField occupies only one column like the previous textField")
    }
  }
}
