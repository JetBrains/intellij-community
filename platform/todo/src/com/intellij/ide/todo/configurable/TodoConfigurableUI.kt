// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.configurable

import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal class TodoConfigurableUI(patternsTableDecorator: ToolbarDecorator, filtersTableDecorator: ToolbarDecorator) {

  lateinit var multiLineCheckBox: JBCheckBox

  @JvmField
  val panel = panel {
    val settings = TodoConfiguration.getInstance()
    row {
      multiLineCheckBox = checkBox(IdeBundle.message("label.todo.multiline"))
        .bindSelected(settings::isMultiLine, settings::setMultiLine)
        .component
    }

    row {
      cell(patternsTableDecorator.createPanel())
        .align(Align.FILL)
        .label(IdeBundle.message("label.todo.patterns"), LabelPosition.TOP)
    }.resizableRow()

    row {
      cell(filtersTableDecorator.createPanel())
        .align(Align.FILL)
        .label(IdeBundle.message("label.todo.filters"), LabelPosition.TOP)
    }.resizableRow()
  }
}
