/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PrimitiveStoredPropertyBase
import com.intellij.openapi.components.StoredProperty
import kotlin.reflect.KProperty

internal class NormalizedStringStoredProperty(override val defaultValue: String?) : PrimitiveStoredPropertyBase<String?>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: String?) {
    val newValue = if (value.isNullOrEmpty()) null else value
    if (this.value != newValue) {
      thisRef.ownModificationCount++
      this.value = newValue
    }
  }

  override fun equals(other: Any?) = this === other || (other is NormalizedStringStoredProperty && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun toString() ="$name = ${if (isEqualToDefault()) "" else value ?: super.toString()}"

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as NormalizedStringStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }
}