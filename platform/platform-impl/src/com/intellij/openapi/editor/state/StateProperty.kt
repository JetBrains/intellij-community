// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A property of an [ObservableState]. The state declares it as a delegate, with `by property(...)`.
 */
abstract class StateProperty<T> : ReadWriteProperty<ObservableState, T> {
  /**
   * The name of the Kotlin property that delegates to this one. It is `null` until the delegation binds it.
   */
  var name: String? = null
    private set

  operator fun provideDelegate(thisRef: Any, property: KProperty<*>): ReadWriteProperty<ObservableState, T> {
    name = property.name
    (thisRef as? ObservableState)?.registerName(this, property.name)
    return this
  }

  override operator fun getValue(thisRef: ObservableState, property: KProperty<*>): T {
    return getValue(thisRef)
  }

  override operator fun setValue(thisRef: ObservableState, property: KProperty<*>, value: T) {
    setValue(thisRef, value)
  }

  abstract fun getValue(thisRef: ObservableState): T

  /**
   * Stores [value] as the override.
   */
  abstract fun setValue(thisRef: ObservableState, value: T)

  /**
   * Removes the override, so that the property holds its default again.
   */
  abstract fun clearOverriding(thisRef: ObservableState)

  /**
   * Computes the value again: the override, or else the default.
   */
  abstract fun recalculate(thisRef: ObservableState)
}
