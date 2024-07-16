// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import org.jetbrains.annotations.ApiStatus
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface StoredProperty<T> {
  var name: String?

  @get:ApiStatus.Internal
  val jsonType: JsonSchemaType

  fun getValue(thisRef: BaseState): T
  fun setValue(thisRef: BaseState, value: T)

  // true if changed
  fun setValue(other: StoredProperty<T>): Boolean

  @ApiStatus.Internal
  fun isEqualToDefault(): Boolean

  @ApiStatus.Internal
  fun getModificationCount(): Long = 0
}

@ApiStatus.Internal
interface ScalarProperty {
  // mod count not changed
  fun parseAndSetValue(rawValue: String?)
}

// type must be exposed otherwise `provideDelegate` doesn't work
abstract class StoredPropertyBase<T> : StoredProperty<T>, ReadWriteProperty<BaseState, T> {
  override var name: String? = null

  operator fun provideDelegate(thisRef: Any, property: KProperty<*>): ReadWriteProperty<BaseState, T> {
    name = property.name
    return this
  }

  fun provideDelegate(thisRef: Any, propertyName: String): StoredProperty<T> {
    name = propertyName
    return this
  }

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>): T = getValue(thisRef)
  override operator fun setValue(thisRef: BaseState, property: KProperty<*>, value: T): Unit = setValue(thisRef, value)
}

@ApiStatus.Internal
enum class JsonSchemaType(val jsonName: String) {
  OBJECT("object"),
  ARRAY("array"),

  STRING("string"),

  INTEGER("integer"),
  NUMBER("number"),

  BOOLEAN("boolean");

  val isScalar: Boolean
    get() = this != OBJECT && this !== ARRAY
}