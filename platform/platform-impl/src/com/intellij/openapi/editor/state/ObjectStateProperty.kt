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
 *
 * The property always reads and changes [state], the state that declares it. It ignores the `thisRef` parameter of each
 * method.
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
    get() = currentValue()

  override fun getValue(thisRef: ObservableState): T {
    return currentValue()
  }

  override fun setValue(thisRef: ObservableState, value: T) {
    state.batch {
      val wasOverridden = state.isOverriddenAt(index)
      if (wasOverridden && state.overrideAt(index) == value) {
        return@batch
      }
      val newValue = modify(value)
      state.storeOverride(index, value)
      storeValue(newValue)
      if (!wasOverridden) {
        state.overridingChanged(name)
      }
    }
  }

  override fun clearOverriding(thisRef: ObservableState) {
    state.batch {
      if (state.isOverriddenAt(index)) {
        val newValue = modify(defaultValueCalculator.calculate())
        state.clearOverrideAt(index)
        storeValue(newValue)
        state.overridingChanged(name)
      }
    }
  }

  override fun recalculate(thisRef: ObservableState) {
    state.batch {
      if (state.isOverriddenAt(index)) {
        // Safe: the entry is not NoOverride here, and only setValue stores an override, which is a T.
        @Suppress("UNCHECKED_CAST")
        storeValue(modify(state.overrideAt(index) as T))
      } else {
        storeValue(modify(defaultValueCalculator.calculate()))
      }
    }
  }

  private fun currentValue(): T {
    // Safe: only this property writes the entry at [index], and it writes only values of type T.
    @Suppress("UNCHECKED_CAST")
    return state.valueAt(index) as T
  }

  /**
   * The value that the property holds for [calculated]: the result of [customOutValueModifier]. A change computes it
   * before it stores anything, so a failure of the modifier or of the calculator changes nothing.
   */
  private fun modify(calculated: T): T {
    return customOutValueModifier?.modifyOutValue(calculated) ?: calculated
  }

  /**
   * Stores [newValue], and notifies the listeners when it differs from the stored value.
   */
  private fun storeValue(newValue: T) {
    val oldValue = state.valueAt(index)
    if (newValue != oldValue) {
      state.storeValue(index, newValue)
      state.propertyChanged(name, Ref(oldValue), newValue)
    }
  }
}
