/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components

import kotlin.reflect.KProperty

internal class IntStoredProperty(override val defaultValue: Int) : PrimitiveStoredPropertyBase<Int>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: Int) {
    if (this.value != value) {
      thisRef.ownModificationCount++
      this.value = value
    }
  }

  override fun equals(other: Any?) = this === other || (other is IntStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (value == defaultValue) "" else value.toString()

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as IntStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }
}

internal class LongStoredProperty(override val defaultValue: Long) : PrimitiveStoredPropertyBase<Long>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: Long) {
    if (this.value != value) {
      thisRef.ownModificationCount++
      this.value = value
    }
  }

  override fun equals(other: Any?) = this === other || (other is LongStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (value == defaultValue) "" else value.toString()

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as LongStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }
}

internal class FloatStoredProperty(override val defaultValue: Float) : PrimitiveStoredPropertyBase<Float>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, value: Float) {
    if (this.value != value) {
      thisRef.ownModificationCount++
      this.value = value
    }
  }

  override fun equals(other: Any?) = this === other || (other is FloatStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (value == defaultValue) "" else value.toString()

  override fun setValue(other: StoredProperty): Boolean {
    val newValue = (other as FloatStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }
}