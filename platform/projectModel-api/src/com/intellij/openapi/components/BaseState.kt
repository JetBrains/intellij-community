/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.components

import com.intellij.configurationStore.properties.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.PropertyAccessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Transient
import gnu.trove.THashMap
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLongFieldUpdater

private val LOG = logger<BaseState>()

abstract class BaseState : SerializationFilter, ModificationTracker {
  companion object {
    private val MOD_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(BaseState::class.java, "ownModificationCount")
  }

  private val properties: MutableList<StoredProperty> = SmartList()

  @Volatile
  @Transient
  private var ownModificationCount: Long = 0

  fun <T> property(): StoredPropertyBase<T?> {
    val result = ObjectStoredProperty<T?>(null)
    properties.add(result)
    return result
  }

  /**
   * Value considered as default only if all properties have default values.
   * Passed instance is not used for `isDefault` check. It is just an initial value.
   */
  fun <T : BaseState?> property(initialValue: T): StoredPropertyBase<T> {
    val result = StateObjectStoredProperty(initialValue)
    properties.add(result)
    return result
  }

  /**
   * For non-BaseState classes explicit `isDefault` must be provided, because no other way to check.
   */
  fun <T> property(initialValue: T, isDefault: (value: T) -> Boolean): StoredPropertyBase<T> {
    val result = object : ObjectStoredProperty<T>(initialValue) {
      override fun isEqualToDefault() = isDefault(value)
    }

    properties.add(result)
    return result
  }

  /**
   * Collection considered as default if empty. It is *your* responsibility to call `incrementModificationCount` on collection modification.
   * You cannot set value to a new collection - on set current collection is cleared and new collection is added to current.
   */
  fun <E, C : MutableCollection<E>> property(initialValue: C): StoredPropertyBase<C> {
    val result = CollectionStoredProperty(initialValue)
    properties.add(result)
    return result
  }

  /**
   * Charset is an immutable, so, it is safe to use it as default value.
   */
  fun <T : Charset> property(initialValue: T): StoredPropertyBase<T> {
    val result = ObjectStoredProperty(initialValue)
    properties.add(result)
    return result
  }

  /**
   * Enum is an immutable, so, it is safe to use it as default value.
   */
  fun <T : Enum<*>> property(defaultValue: T): StoredPropertyBase<T> {
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
    @Suppress("UNCHECKED_CAST")
    return result as StoredPropertyBase<MutableList<T>>
  }

  fun <K : Any, V: Any> property(value: MutableMap<K, V>): StoredPropertyBase<MutableMap<K, V>> {
    return map(value)
  }

  fun <K : Any, V: Any> map(value: MutableMap<K, V> = THashMap()): StoredPropertyBase<MutableMap<K, V>> {
    val result = MapStoredProperty(value)
    properties.add(result)
    return result
  }

  /**
   * Empty string is always normalized to null.
   */
  fun property(defaultValue: String?) = string(defaultValue)

  /**
   * Empty string is always normalized to null.
   */
  fun string(defaultValue: String? = null): StoredPropertyBase<String?> {
    val result = NormalizedStringStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  fun property(defaultValue: Int = 0): StoredPropertyBase<Int> {
    val result = IntStoredProperty(defaultValue, null)
    properties.add(result)
    return result
  }

  fun property(defaultValue: Long = 0): StoredPropertyBase<Long> {
    val result = LongStoredProperty(defaultValue, null)
    properties.add(result)
    return result
  }

  fun property(defaultValue: Float = 0f, valueNormalizer: ((value: Float) -> Float)? = null): StoredPropertyBase<Float> {
    val result = FloatStoredProperty(defaultValue, valueNormalizer)
    properties.add(result)
    return result
  }

  fun property(defaultValue: Boolean = false): StoredPropertyBase<Boolean> {
    val result = ObjectStoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  // reset on load state
  fun resetModificationCount() {
    ownModificationCount = 0
  }

  protected fun incrementModificationCount() {
    intIncrementModificationCount()
  }

  internal fun intIncrementModificationCount() {
    MOD_COUNT_UPDATER.incrementAndGet(this)
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

  internal fun isEqualToDefault() = properties.all { it.isEqualToDefault() }

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
