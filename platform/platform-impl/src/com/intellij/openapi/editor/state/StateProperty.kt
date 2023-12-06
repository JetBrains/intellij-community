// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


abstract class StateProperty<T> : ReadWriteProperty<ObservableState, T> {

  var name: String? = null

  operator fun provideDelegate(thisRef: Any, property: KProperty<*>): ReadWriteProperty<ObservableState, T> {
    name = property.name
    return this
  }
  override operator fun getValue(thisRef: ObservableState, property: KProperty<*>): T = getValue(thisRef)
  override operator fun setValue(thisRef: ObservableState, property: KProperty<*>, value: T): Unit = setValue(thisRef, value)

  abstract fun getValue(thisRef: ObservableState): T
  abstract fun setValue(thisRef: ObservableState, value: T)
  abstract fun clearOverriding(thisRef: ObservableState)
  abstract fun recalculate(thisRef: ObservableState)
}