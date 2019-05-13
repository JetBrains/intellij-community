// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import org.jetbrains.annotations.ApiStatus
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@ApiStatus.Experimental
interface StoredProperty<T> {
  var name: String?

  val jsonType: JsonSchemaType

  // true if changed
  fun setValue(other: StoredProperty<T>): Boolean

  fun isEqualToDefault(): Boolean

  fun getModificationCount(): Long = 0
}

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
}

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