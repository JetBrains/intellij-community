// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.*
import javax.swing.text.JTextComponent

internal const val DSL_LABEL_NO_BOTTOM_GAP_PROPERTY = "dsl.label.no.bottom.gap"

private val LABELED_COMPONENTS = listOf(
  JComboBox::class,
  JSlider::class,
  JSpinner::class,
  JTextComponent::class
)

internal val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (cell is CellImpl<*>) {
    val component = cell.component
    if (LABELED_COMPONENTS.any { clazz -> clazz.isInstance(component) }) {
      label.labelFor = component
    }
  }
}
