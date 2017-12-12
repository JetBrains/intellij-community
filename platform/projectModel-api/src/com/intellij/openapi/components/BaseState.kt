/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.PropertyAccessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Transient

private val LOG = logger<BaseState>()

abstract class BaseState : SerializationFilter, ModificationTracker {
  private val properties: MutableList<StoredProperty> = SmartList()

  @Volatile
  @Transient
  @JvmField
  internal var ownModificationCount: Long = 0

  // reset on load state
  fun resetModificationCount() {
    ownModificationCount = 0
  }

  protected fun incrementModificationCount() {
    ownModificationCount++
  }

  fun <T> storedProperty(defaultValue: T? = null): StoredPropertyBase<T?> {
    val result = ObjectStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  /**
   * Not-null list. Initialized as SmartList.
   */
  fun <T : Any> list(): StoredPropertyBase<MutableList<T>> {
    val result = ListStoredProperty<T>()
    properties.add(result)
    return result
  }

  fun <K: Any, V> map(): StoredPropertyBase<MutableMap<K, V>> {
    val result = MapStoredProperty<K, V>()
    properties.add(result)
    return result
  }

  fun <T : Any> bean(defaultValue: T): StoredPropertyBase<T> {
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

  fun storedProperty(defaultValue: Long = 0): StoredPropertyBase<Long> {
    val result = LongStoredProperty(defaultValue)
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
    val getterName = (accessor as? PropertyAccessor)?.getterName
    for (property in properties) {
      if (property.name == accessor.name || property.name == getterName) {
        return !property.isEqualToDefault()
      }
    }

    LOG.debug("Cannot find property by name: ${accessor.name}")
    // do not return false - maybe accessor delegates actual set to our property
    // default value in this case will be filtered by common filter (instance will be created in this case, as for non-smart state classes)
    return true
  }

  @Transient
  override fun getModificationCount(): Long {
    var result = ownModificationCount
    for (property in properties) {
      result += property.getModificationCount()
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
      builder.append(property.toString()).append(" ")
    }
    builder.setLength(builder.length - 1)
    return builder.toString()
  }

  fun copyFrom(state: BaseState) {
    LOG.assertTrue(state.properties.size == properties.size)
    var changed = false
    for ((index, property) in properties.withIndex()) {
      val otherProperty = state.properties.get(index)
      LOG.assertTrue(otherProperty.name == property.name)
      if (property.setValue(otherProperty)) {
        changed = true
      }
    }

    if (changed) {
      incrementModificationCount()
    }
  }
}
