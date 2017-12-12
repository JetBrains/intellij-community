/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components

import com.intellij.openapi.util.ModificationTracker
import kotlin.reflect.KProperty

internal class ObjectStoredProperty<T>(private val defaultValue: T) : StoredPropertyBase<T>() {
  private var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>): T = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: T) {
    if (this.value != value) {
      thisRef.ownModificationCount++
      this.value = value
    }
  }

  override fun isEqualToDefault() = defaultValue == value

  override fun equals(other: Any?) = this === other || (other is ObjectStoredProperty<*> && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun toString() = if (isEqualToDefault()) "" else value?.toString() ?: super.toString()

  override fun setValue(other: StoredProperty): Boolean {
    @Suppress("UNCHECKED_CAST")
    val newValue = (other as ObjectStoredProperty<T>).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun getModificationCount(): Long {
    val value = value
    return if (value is ModificationTracker) value.modificationCount else 0
  }
}

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

  override fun toString() = if (isEqualToDefault()) "" else value ?: super.toString()

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as NormalizedStringStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }
}