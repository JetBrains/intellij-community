// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref

open class ObjectStateProperty<T>(
  initialValue: T,
  private val defaultValueCalculator: SyncDefaultValueCalculator<T>,
  private val customOutValueModifier: CustomOutValueModifier<T>?,
) : StateProperty<T>() {
  private var overriddenValue: T? = null
  private var outValue: T = initialValue

  var isValueOverridden: Boolean = false  // this is need for supporting possible `null` values
    private set

  protected val value: T get() = outValue

  override fun getValue(thisRef: ObservableState): T {
    return this.outValue
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
      overriddenValue = null  // avoid memory leak
      recalculate(thisRef)
    }
  }

  override fun recalculate(thisRef: ObservableState) {
    if (isValueOverridden) {
      @Suppress("UNCHECKED_CAST")
      updateOutValueAndFireEventIfNeed(thisRef, overriddenValue as T)
      return
    }
    val newVal = defaultValueCalculator.calculate()
    updateOutValueAndFireEventIfNeed(thisRef, newVal)
  }

  private fun updateOutValueAndFireEventIfNeed(thisRef: ObservableState, calculatedOutValue: T) {
    val newOutValue = customOutValueModifier?.modifyOutValue(calculatedOutValue) ?: calculatedOutValue
    if (newOutValue != this.outValue) {
      val oldOut = this.outValue
      this.outValue = newOutValue
      thisRef.propertyChanged(name, Ref(oldOut), newOutValue)
    }
  }
}
