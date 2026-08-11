// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.ui.dsl.builder.ButtonsGroup
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.ButtonGroup
import javax.swing.JRadioButton

internal class ButtonsGroupImpl(panel: PanelImpl, startIndex: Int) : RowsRangeImpl(panel, startIndex), ButtonsGroup {

  private val radioButtons = mutableMapOf<CellImpl<out JRadioButton>, Any?>()
  private var groupBinding: GroupBinding<*>? = null

  override fun visible(isVisible: Boolean): ButtonsGroup {
    super.visible(isVisible)
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): ButtonsGroup {
    super.visibleIf(predicate)
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): ButtonsGroup {
    return visibleIf(ComponentPredicate.fromObservableProperty(property))
  }

  override fun enabled(isEnabled: Boolean): ButtonsGroup {
    super.enabled(isEnabled)
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): ButtonsGroup {
    super.enabledIf(predicate)
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): ButtonsGroup {
    return enabledIf(ComponentPredicate.fromObservableProperty(property))
  }

  override fun <T> bind(prop: MutableProperty<T>, type: Class<T>): ButtonsGroup {
    checkNull(groupBinding) { "The group is bound already" }

    groupBinding = GroupBinding(prop, type)
    return this
  }

  fun add(cell: CellImpl<out JRadioButton>, value: Any? = null) {
    radioButtons[cell] = value
  }

  fun postInit() {
    if (groupBinding == null) {
      postInitUnbound()
    }
    else {
      postInitBound(groupBinding!!)
    }
  }

  private fun postInitBound(groupBinding: GroupBinding<*>) {
    val buttonGroup = ButtonGroup()
    for ((cell, value) in radioButtons) {
      checkNotNull(value) { "Radio button '${cell.component.text}' is used without value for binding" }

      groupBinding.validate(value)
      buttonGroup.add(cell.component)

      cell.onChangeManager.applyBinding {
        cell.component.isSelected = groupBinding.prop.get() == value
      }

      cell.onApply {
        if (cell.component.isSelected) groupBinding.set(value)
      }
      cell.onReset {
        cell.onChangeManager.applyBinding {
          cell.component.isSelected = groupBinding.prop.get() == value
        }
      }
      cell.onIsModified {
        cell.component.isSelected != (groupBinding.prop.get() == value)
      }
    }
  }

  private fun postInitUnbound() {
    val buttonGroup = ButtonGroup()
    for ((cell, value) in radioButtons) {
      checkNull(value) { "Radio button '${cell.component.text}' is used without ButtonsGroup.bind" }

      buttonGroup.add(cell.component)
    }
  }
}

private class GroupBinding<T>(val prop: MutableProperty<T>, val type: Class<T>) {

  fun set(value: Any) {
    prop.set(type.cast(value))
  }

  fun validate(value: Any) {
    check(type.isInstance(value)) { "Value $value is incompatible with button group binding class ${type.simpleName}" }
  }
}
