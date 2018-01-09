/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.configurationStore.properties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import com.intellij.openapi.util.ModificationTracker
import kotlin.reflect.KProperty

internal abstract class ObjectStateStoredPropertyBase<T>(protected var value: T) : StoredPropertyBase<T>() {
  override operator fun getValue(thisRef: BaseState, property: KProperty<*>): T = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: T) {
    if (value != newValue) {
      thisRef.intIncrementModificationCount()
      value = newValue
    }
  }

  override fun setValue(other: StoredProperty): Boolean {
    @Suppress("UNCHECKED_CAST")
    val newValue = (other as ObjectStateStoredPropertyBase<T>).value
    return if (newValue == value) {
      false
    }
    else {
      value = newValue
      true
    }
  }

  override fun equals(other: Any?) = this === other || (other is ObjectStateStoredPropertyBase<*> && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun toString() = "$name = ${if (isEqualToDefault()) "" else value?.toString() ?: super.toString()}"
}

internal open class ObjectStoredProperty<T>(private val defaultValue: T) : ObjectStateStoredPropertyBase<T>(defaultValue) {
  override fun isEqualToDefault(): Boolean {
    val value = value
    return defaultValue == value || (value as? BaseState)?.isEqualToDefault() ?: false
  }

  override fun getModificationCount() = (value as? ModificationTracker)?.modificationCount ?: 0
}

internal class StateObjectStoredProperty<T : BaseState?>(initialValue: T) : ObjectStateStoredPropertyBase<T>(initialValue) {
  override fun isEqualToDefault(): Boolean {
    val value = value
    return value == null || value.isEqualToDefault()
  }

  override fun getModificationCount() = value?.modificationCount ?: 0
}