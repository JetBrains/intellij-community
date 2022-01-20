// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI

@Suppress("DialogTitleCapitalization")
@Demo(title = "Tips",
  description = "Here are some useful tips and tricks",
  scrollbar = true)
fun demoTips(parentDisposable: Disposable): DialogPanel {
  val panel = panel {
    row {
      label("Bold text")
        .bold()
    }.rowComment("Use Cell.bold method for bold text, works for any component")

    row {
      textField()
        .columns(COLUMNS_MEDIUM)
    }.rowComment("Configure width of textField, comboBox and textArea by columns method")

    row {
      textField()
        .text("Initialized text")
    }.rowComment("Set initial text of text component by text method")

    row("intTextField(0..1000, 100):") {
      intTextField(0..1000, 100)
        .text("500")
    }.rowComment("Use Row.intTextField for input integer numbers. There is value validation, range validation and " +
                 "supported up/down keys")

    row("Some text") {
      textField()
        .resizableColumn()
      link("Config...") {}
      link("About") {}
    }.rowComment("Use Cell.resizableColumn if column should occupy whole free space. Remaining cells are adjusted to the right")

    row {
      text("If needed text color of Row.text() can be changed to comment color with the following code:<br>" +
           "foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND<br>" +
           "It differs from Row.comment() because comments can use smaller font size on macOS and Linux")
        .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
    }

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

    group("Use Panel.row(EMPTY_LABEL) if label is empty") {
      row("Row 1:") {
        textField()
      }
      row(EMPTY_LABEL) {
        textField()
      }.rowComment("""Don't use row(""), because it creates unnecessary label component in layout""")
    }
  }

  val disposable = Disposer.newDisposable()
  panel.registerValidators(disposable)
  Disposer.register(parentDisposable, disposable)

  return panel
}
