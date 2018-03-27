/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.configurationStore.properties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import kotlin.reflect.KProperty

internal class IntStoredProperty(private val defaultValue: Int, private val valueNormalizer: ((value: Int) -> Int)?) : StoredPropertyBase<Int>() {
  private var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: Int) {
    val newValue = valueNormalizer?.invoke(value) ?: value
    if (this.value != newValue) {
      thisRef.intIncrementModificationCount()
      this.value = newValue
    }
  }

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as IntStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun equals(other: Any?) = this === other || (other is IntStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = "$name = $value${if (value == defaultValue) " (default)" else ""}"

  override fun isEqualToDefault() = value == defaultValue
}