// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.properties

import com.intellij.openapi.components.*
import kotlin.reflect.KProperty

internal class NormalizedStringStoredProperty(private val defaultValue: String?) : StoredPropertyBase<String?>(), ScalarProperty {
  private var value = defaultValue

  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.STRING

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: String?) {
    val newValue = if (value.isNullOrEmpty()) null else value
    if (this.value != newValue) {
      thisRef.intIncrementModificationCount()
      this.value = newValue
    }
  }

  override fun setValue(other: StoredProperty<String?>): Boolean {
    val newValue = (other as NormalizedStringStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun equals(other: Any?) = this === other || (other is NormalizedStringStoredProperty && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun isEqualToDefault() = value == defaultValue

  override fun toString() = "$name = $value${if (value == defaultValue) " (default)" else ""}"

  override fun parseAndSetValue(rawValue: String?) {
    value = rawValue
  }
}