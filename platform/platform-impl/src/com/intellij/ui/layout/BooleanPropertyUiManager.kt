// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.util.SmartList
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.ComboBoxModel
import javax.swing.JComponent
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

open class BooleanPropertyUiManager {
  private var _value = false
  private var checkBox: AbstractButton? = null
  protected val dependentComponents: MutableList<JComponent> = SmartList()

  var value: Boolean
    get() = _value
    set(value) {
      _value = value
      checkBox?.isSelected = value
      updateDependentComponentState()
    }

  var isEnabled = true
    set(value) {
      field = value
      updateCheckBoxEnabledState()
    }

  fun registerCheckBox(component: AbstractButton) {
    assert(checkBox == null)

    checkBox = component
    component.addActionListener(ActionListener {
      _value = component.isSelected
      updateDependentComponentState()
    })

    updateCheckBoxEnabledState()
  }

  private fun updateDependentComponentState() {
    for (component in dependentComponents) {
      component.isEnabled = value
    }
  }

  open fun manage(component: JComponent) {
    component.isEnabled = value
    dependentComponents.add(component)
  }

  private fun updateCheckBoxEnabledState() {
    checkBox?.isEnabled = isEnabled
  }
}

open class BooleanPropertyWithListUiManager<I, T : ListModel<I>>(val listModel: T) : BooleanPropertyUiManager() {
  private var isModelListenerAdded = false

  private fun addModelListener() {
    if (isModelListenerAdded || dependentComponents.isEmpty()) {
      return
    }

    isModelListenerAdded = true

    listModel.addListDataListener(object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent) {
        updateState()
      }

      override fun intervalRemoved(e: ListDataEvent) {
        updateState()
      }

      override fun intervalAdded(e: ListDataEvent) {
        updateState()
      }

      private fun updateState() {
        for (component in dependentComponents) {
          component.isVisible = isDependentComponentVisible()
        }
      }
    })
  }

  override fun manage(component: JComponent) {
    super.manage(component)
    component.isVisible = isDependentComponentVisible()
    addModelListener()
  }

  private fun isDependentComponentVisible() = listModel.size > 0
}

open class BooleanPropertyWithComboBoxUiManager<I, T : ComboBoxModel<I>>(listModel: T) : BooleanPropertyWithListUiManager<I, T>(listModel) {
  @Suppress("UNCHECKED_CAST")
  var selected: I?
    get() = if (value) listModel.selectedItem as I? else null
    set(value) {
      listModel.selectedItem = value
    }
}