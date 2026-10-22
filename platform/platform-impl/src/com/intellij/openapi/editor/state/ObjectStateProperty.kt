// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref

/**
 * A [StateProperty] that holds its value in memory.
 *
 * The value is the override, or else the result of [defaultValueCalculator]. Then [customOutValueModifier] can change
 * it. A change of the value notifies the [ObservableState].
 */
open class ObjectStateProperty<T>(
  initialValue: T,
  private val defaultValueCalculator: SyncDefaultValueCalculator<T>,
  private val customOutValueModifier: CustomOutValueModifier<T>?,
) : StateProperty<T>() {
  private var overriddenValue: T? = null
  private var outValue: T = initialValue

  /**
   * Whether the property has an override. This is a separate flag, because `null` can be an override.
   */
  var isValueOverridden: Boolean = false
    private set

  /**
   * The current value, after [customOutValueModifier].
   */
  protected val value: T
    get() = outValue

  override fun getValue(thisRef: ObservableState): T {
    return outValue
  }

  override fun setValue(thisRef: ObservableState, value: T) {
    if (isValueOverridden && overriddenValue == value) {
      return
    }
    overriddenValue = value
    isValueOverridden = true
    recalculate(thisRef)
  }

  override fun clearOverriding(thisRef: ObservableState) {
    if (isValueOverridden) {
      isValueOverridden = false
      // Drop the reference, so that the old value can be collected.
      overriddenValue = null
      recalculate(thisRef)
    }
  }

  override fun recalculate(thisRef: ObservableState) {
    if (isValueOverridden) {
      @Suppress("UNCHECKED_CAST")
      updateOutValue(thisRef, overriddenValue as T)
    } else {
      updateOutValue(thisRef, defaultValueCalculator.calculate())
    }
  }

  /**
   * Stores [calculated] after [customOutValueModifier], and notifies [thisRef] when the stored value changes.
   */
  private fun updateOutValue(thisRef: ObservableState, calculated: T) {
    val newOutValue = customOutValueModifier?.modifyOutValue(calculated) ?: calculated
    if (newOutValue != outValue) {
      val oldOutValue = outValue
      outValue = newOutValue
      thisRef.propertyChanged(name, Ref(oldOutValue), newOutValue)
    }
  }
}
