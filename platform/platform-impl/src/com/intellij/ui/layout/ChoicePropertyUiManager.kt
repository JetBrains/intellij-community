// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import java.awt.event.ActionListener
import javax.swing.ButtonModel
import javax.swing.JRadioButton

class ChoicePropertyUiManager<T : Any>(defaultChoice: T) {
  private val components = ArrayList<ChoiceInfo<T>>()

  private var currentSelection: ChoiceInfo<T>? = null

  private var _selected: T = defaultChoice
  var selected: T
    get() = _selected
    set(value) {
      updateSelection(components.firstOrNull { it.id == value })
    }

  private fun updateSelection(newSelection: ChoiceInfo<T>?) {
    currentSelection?.select(false)

    currentSelection = newSelection ?: return
    newSelection.select(true)
  }

  internal fun addRadioButton(component: JRadioButton, id: T, row: Row) {
    val isSelected = id == _selected
    component.isSelected = isSelected
    val info = ChoiceInfo(id, component.model, row)
    components.add(info)
    if (isSelected) {
      currentSelection = info
    }
    else {
      row.subRowsEnabled = false
    }

    component.addActionListener(ActionListener {
      if (component.isSelected) {
        updateSelection(info)
      }
    })
  }
}

private data class ChoiceInfo<T>(val id: T, val model: ButtonModel, val row: Row) {
  fun select(value: Boolean) {
    model.isSelected = value
    row.subRowsEnabled = value
  }
}