// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class QuickListContent(list: JComponent) {

  lateinit var name: JBTextField
  lateinit var description: JBTextField

  val content = panel {
    row(KeyMapBundle.message("quick.list.panel.display.name.label")) {
      name = textField()
        .align(AlignX.FILL)
        .component
    }
    row(KeyMapBundle.message("quick.list.panel.description.label")) {
      description = textField()
        .align(AlignX.FILL)
        .component
    }
    row {
      cell(list)
        .align(Align.FILL)
        .applyToComponent {
          putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = true, bottom = false))
        }
    }.resizableRow()
  }
}