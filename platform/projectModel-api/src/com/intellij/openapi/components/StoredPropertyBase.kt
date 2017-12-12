/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal interface StoredProperty {
  val value: Any?

  var name: String?

  // true if changed
  fun setValue(other: StoredProperty): Boolean

  fun isEqualToDefault(): Boolean
}

// type must be exposed otherwise `provideDelegate` doesn't work
abstract class StoredPropertyBase<T> : ReadWriteProperty<BaseState, T>, StoredProperty {
  override var name: String? = null

  operator fun provideDelegate(thisRef: Any, property: KProperty<*>): ReadWriteProperty<BaseState, T> {
    name = property.name
    return this
  }
}

internal abstract class PrimitiveStoredPropertyBase<T> : StoredPropertyBase<T>() {
  protected abstract val defaultValue: Any?

  override fun isEqualToDefault() = value == defaultValue
}