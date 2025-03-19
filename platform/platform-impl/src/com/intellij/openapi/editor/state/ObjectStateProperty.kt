// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref

open class ObjectStateProperty<T>(initialValue: T,
                                  private val defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                  private val customOutValueModifier: CustomOutValueModifier<T>? = null) : StateProperty<T>() {
  private var overriddenValue: T? = null
  private var isValueOverridden = false  // this is need for supporting possible `null` values
  private var outValue: T = initialValue

  protected val value get() = outValue

  override fun getValue(thisRef: ObservableState): T = this.outValue

  override fun setValue(thisRef: ObservableState, value: T) {
    if (isValueOverridden && overriddenValue == value) return

    overriddenValue = value
    isValueOverridden = true
    recalculate(thisRef)
  }

  override fun clearOverriding(thisRef: ObservableState) {
    if (!isValueOverridden) return

    isValueOverridden = false
    overriddenValue = null  // avoid memory leak
    recalculate(thisRef)
  }

  override fun recalculate(thisRef: ObservableState) {
    if (isValueOverridden) {
      @Suppress("UNCHECKED_CAST")
      updateOutValueAndFireEventIfNeed(thisRef, overriddenValue as T)
      return
    }

    if (defaultValueCalculator is SyncDefaultValueCalculator) {
      val newVal = defaultValueCalculator.calculate()
      updateOutValueAndFireEventIfNeed(thisRef, newVal)
      return
    }

    // fixme log error illegal state
    throw IllegalStateException(defaultValueCalculator.toString())
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