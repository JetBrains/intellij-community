// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref

/**
 * A [StateProperty] with a default and an optional override.
 *
 * The value is the override, or else the result of [defaultValueCalculator]. Then [customOutValueModifier] can change
 * the value.
 *
 * - [setValue] stores an override. An override equal to the current one changes nothing.
 * - [clearOverriding] removes the override, so that the property holds its default again.
 * - [recalculate] computes the value again, for example after a change of a source of the default.
 *
 * The listeners of the state get a value event when the new value is not equal to the old one. They get an override
 * event when the property gets or loses its override, also when the value stays the same.
 */
open class ObjectStateProperty<T> internal constructor(
  private val state: ObservableState,
  index: Int,
  initialValue: T,
  private val defaultValueCalculator: SyncDefaultValueCalculator<T>,
  private val customOutValueModifier: CustomOutValueModifier<T>?,
) : StateProperty<T>(index, initialValue) {
  /**
   * Whether the property has an override. An override can be `null`.
   */
  val isValueOverridden: Boolean
    get() = state.isOverriddenAt(index)

  /**
   * The current value.
   */
  protected val value: T
    get() = getValue(state)

  override fun getValue(thisRef: ObservableState): T {
    // Safe: only this property writes the entry at [index], and it writes only values of type T.
    @Suppress("UNCHECKED_CAST")
    return thisRef.valueAt(index) as T
  }

  override fun setValue(thisRef: ObservableState, value: T) {
    thisRef.batch {
      val wasOverridden = thisRef.isOverriddenAt(index)
      if (wasOverridden && thisRef.overrideAt(index) == value) {
        return@batch
      }
      thisRef.storeOverride(index, value)
      updateValue(thisRef, value)
      if (!wasOverridden) {
        thisRef.overridingChanged(name)
      }
    }
  }

  override fun clearOverriding(thisRef: ObservableState) {
    thisRef.batch {
      if (thisRef.isOverriddenAt(index)) {
        thisRef.clearOverrideAt(index)
        updateValue(thisRef, defaultValueCalculator.calculate())
        thisRef.overridingChanged(name)
      }
    }
  }

  override fun recalculate(thisRef: ObservableState) {
    thisRef.batch {
      if (thisRef.isOverriddenAt(index)) {
        // Safe: the entry is not NoOverride here, and only setValue stores an override, which is a T.
        @Suppress("UNCHECKED_CAST")
        updateValue(thisRef, thisRef.overrideAt(index) as T)
      } else {
        updateValue(thisRef, defaultValueCalculator.calculate())
      }
    }
  }

  /**
   * Stores [calculated] after [customOutValueModifier], and notifies [thisRef] when the stored value changes.
   */
  private fun updateValue(thisRef: ObservableState, calculated: T) {
    val newValue = customOutValueModifier?.modifyOutValue(calculated) ?: calculated
    val oldValue = thisRef.valueAt(index)
    if (newValue != oldValue) {
      thisRef.storeValue(index, newValue)
      thisRef.propertyChanged(name, Ref(oldValue), newValue)
    }
  }
}
