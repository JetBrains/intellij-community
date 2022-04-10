// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.ButtonsGroup
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import org.jetbrains.annotations.ApiStatus
import javax.swing.AbstractButton
import javax.swing.ButtonGroup

@ApiStatus.Internal
internal class ButtonsGroupImpl : ButtonsGroup {

  private val unboundRadioButtons = mutableSetOf<Cell<AbstractButton>>()
  private val boundRadioButtons = mutableMapOf<Cell<AbstractButton>, Any>()
  private var groupBinding: GroupBinding<*>? = null

  override fun <T> bind(prop: MutableProperty<T>, type: Class<T>): ButtonsGroup {
    if (groupBinding != null) {
      throw UiDslException("The group is bound already")
    }
    groupBinding = GroupBinding(prop, type)
    return this
  }

  fun <T : AbstractButton> add(cell: Cell<T>, value: Any? = null) {
    if (value == null) {
      unboundRadioButtons += cell
    }
    else {
      boundRadioButtons[cell] = value
    }
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
    if (unboundRadioButtons.isNotEmpty()) {
      throw UiDslException("Radio button '${unboundRadioButtons.first().component.text}' is used without value for binding")
    }

    val buttonGroup = ButtonGroup()
    for ((cell, value) in boundRadioButtons) {
      groupBinding.validate(value)
      buttonGroup.add(cell.component)

      cell.component.isSelected = groupBinding.prop.get() == value
      cell.onApply { if (cell.component.isSelected) groupBinding.set(value) }
      cell.onReset { cell.component.isSelected = groupBinding.prop.get() == value }
      cell.onIsModified { cell.component.isSelected != (groupBinding.prop.get() == value) }
    }
  }

  private fun postInitUnbound() {
    if (boundRadioButtons.isNotEmpty()) {
      throw UiDslException("Radio button '${boundRadioButtons.keys.first().component.text}' is used without ButtonsGroup.bind")
    }

    val buttonGroup = ButtonGroup()
    for (cell in unboundRadioButtons) {
      buttonGroup.add(cell.component)
    }
  }
}

private class GroupBinding<T>(val prop: MutableProperty<T>, val type: Class<T>) {

  fun set(value: Any) {
    prop.set(type.cast(value))
  }

  fun validate(value: Any) {
    if (!type.isInstance(value)) {
      throw UiDslException("Value $value is incompatible with button group binding class ${type.simpleName}")
    }
  }
}
