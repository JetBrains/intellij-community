/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components

import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Transient
import kotlin.reflect.KProperty

abstract class BaseState : SerializationFilter, ModificationTracker {
  // if property value differs from default
  private val properties: MutableList<StoredProperty> = SmartList()

  @Volatile
  internal var modificationCount: Long = 0

  // reset on load state
  fun resetModificationCount() {
    modificationCount = 0
  }

  protected fun incrementModificationCount() {
    modificationCount++
  }

  fun <T> storedProperty(defaultValue: T? = null): StoredPropertyBase<T?> {
    val result = ObjectStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  /**
   * Empty string is always normalized to null.
   */
  fun string(defaultValue: String? = null): StoredPropertyBase<String?> {
    val result = NormalizedStringStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  fun storedProperty(defaultValue: Int = 0): StoredPropertyBase<Int> {
    val result = IntStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  fun storedProperty(defaultValue: Float = 0f): StoredPropertyBase<Float> {
    val result = FloatStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  fun storedProperty(defaultValue: Boolean = false): StoredPropertyBase<Boolean> {
    val result = ObjectStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  override fun accepts(accessor: Accessor, bean: Any): Boolean {
    for (property in properties) {
      if (property.name == accessor.name) {
        return property.value != property.defaultValue
      }
    }
    return false
  }

  @Transient
  override fun getModificationCount(): Long {
    var result = modificationCount
    for (property in properties) {
      val value = property.value
      if (value is ModificationTracker) {
        result += value.modificationCount
      }
    }
    return result
  }

  override fun equals(other: Any?) = this === other || (other is BaseState && properties == other.properties)

  override fun hashCode() = properties.hashCode()

  override fun toString(): String {
    if (properties.isEmpty()) {
      return ""
    }

    val builder = StringBuilder()
    for (property in properties) {
      builder.append(property.value).append(" ")
    }
    builder.setLength(builder.length - 1)
    return builder.toString()
  }

  fun copyFrom(state: BaseState) {
    assert(state.properties.size == properties.size)
    for ((index, property) in properties.withIndex()) {
      val otherProperty = state.properties.get(index)
      assert(otherProperty.name == property.name)
      property.setValue(otherProperty)
    }
  }
}

private class ObjectStoredProperty<T>(override val defaultValue: T) : StoredPropertyBase<T>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  @Suppress("UNCHECKED_CAST")
  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: T) {
    if (value != newValue) {
      thisRef.modificationCount++

      value = newValue
    }
  }

  override fun equals(other: Any?) = this === other || (other is ObjectStoredProperty<*> && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun toString() = if (value == defaultValue) "" else value?.toString() ?: super.toString()

  override fun setValue(other: StoredProperty) {
    @Suppress("UNCHECKED_CAST")
    value = (other as ObjectStoredProperty<T>).value
  }
}

private class NormalizedStringStoredProperty(override val defaultValue: String?) : StoredPropertyBase<String?>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  @Suppress("UNCHECKED_CAST")
  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") _newValue: String?) {
    var newValue = _newValue
    if (newValue != null && newValue.isEmpty()) {
      newValue = null
    }

    if (value != newValue) {
      thisRef.modificationCount++
      value = newValue
    }
  }

  override fun equals(other: Any?) = this === other || (other is NormalizedStringStoredProperty && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun toString() = if (value == defaultValue) "" else value ?: super.toString()

  override fun setValue(other: StoredProperty) {
    value = (other as NormalizedStringStoredProperty).value
  }
}

private class IntStoredProperty(override val defaultValue: Int) : StoredPropertyBase<Int>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  @Suppress("UNCHECKED_CAST")
  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: Int) {
    if (value != newValue) {
      thisRef.modificationCount++

      value = newValue
    }
  }

  override fun equals(other: Any?) = this === other || (other is IntStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (value == defaultValue) "" else value.toString()

  override fun setValue(other: StoredProperty) {
    value = (other as IntStoredProperty).value
  }
}

private class FloatStoredProperty(override val defaultValue: Float) : StoredPropertyBase<Float>() {
  override var value = defaultValue

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  @Suppress("UNCHECKED_CAST")
  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: Float) {
    if (value != newValue) {
      thisRef.modificationCount++

      value = newValue
    }
  }

  override fun equals(other: Any?) = this === other || (other is FloatStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (value == defaultValue) "" else value.toString()

  override fun setValue(other: StoredProperty) {
    value = (other as FloatStoredProperty).value
  }
}