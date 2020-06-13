// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.serialization.PropertyAccessor
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

internal val LOG = logger<BaseState>()

private val factory: StatePropertyFactory = ServiceLoader.load(StatePropertyFactory::class.java, BaseState::class.java.classLoader).first()

abstract class BaseState : SerializationFilter, ModificationTracker {
  companion object {
    // should be part of class and not file level to access private field of class
    private val MOD_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(BaseState::class.java, "ownModificationCount")
  }

  // do not use SmartList because most objects have more than 1 property
  private val properties: MutableList<StoredProperty<Any>> = ArrayList()

  @Volatile
  @Transient
  private var ownModificationCount = 0L

  private fun <T> addProperty(p: StoredPropertyBase<T>): StoredPropertyBase<T> {
    @Suppress("UNCHECKED_CAST")
    properties.add(p as StoredPropertyBase<Any>)
    return p
  }

  @Suppress("RemoveExplicitTypeArguments")
  protected fun <T : BaseState> property(): StoredPropertyBase<T?> = addProperty(factory.stateObject<T?>(null))

  /**
   * Value considered as default only if all properties have default values.
   * Passed instance is not used for `isDefault` check. It is just an initial value.
   */
  protected fun <T : BaseState?> property(initialValue: T): StoredPropertyBase<T> = addProperty(factory.stateObject(initialValue))

  /**
   * For non-BaseState classes explicit `isDefault` must be provided, because no other way to check.
   */
  protected fun <T> property(initialValue: T, isDefault: (value: T) -> Boolean) = addProperty(factory.obj(initialValue, isDefault))

  /**
   * Collection considered as default if empty. It is *your* responsibility to call `incrementModificationCount` on collection modification.
   * You cannot set value to a new collection - on set current collection is cleared and new collection is added to current.
   */
  protected fun stringSet(): StoredPropertyBase<MutableSet<String>> = addProperty(factory.stringSet(null))

  /**
   * Collection considered as default if contains only the specified default value. It is *your* responsibility to call `incrementModificationCount` on collection modification.
   * You cannot set value to a new collection - on set current collection is cleared and new collection is added to current.
   */
  protected fun stringSet(defaultValue: String): StoredPropertyBase<MutableSet<String>> = addProperty(factory.stringSet(defaultValue))

  /**
   * Collection considered as default if empty. It is *your* responsibility to call `incrementModificationCount` on collection modification.
   * You cannot set value to a new collection - on set current collection is cleared and new collection is added to current.
   */
  protected fun <E> treeSet(): StoredPropertyBase<MutableSet<E>> where E : Comparable<E>, E : BaseState = addProperty(factory.treeSet<E>())

  /**
   * Charset is an immutable, so, it is safe to use it as default value.
   */
  protected fun <T : Charset> property(initialValue: T) = addProperty(factory.obj(initialValue))

  // Enum is an immutable, so, it is safe to use it as default value.
  @Deprecated(message = "Use [enum] instead", replaceWith = ReplaceWith("enum(defaultValue)"), level = DeprecationLevel.ERROR)
  protected fun <T : Enum<*>> property(defaultValue: T) = addProperty(factory.obj(defaultValue))

  protected inline fun <reified T : Enum<*>> enum(defaultValue: T): StoredPropertyBase<T> {
    @Suppress("UNCHECKED_CAST")
    return doEnum(defaultValue, T::class.java) as StoredPropertyBase<T>
  }

  protected inline fun <reified T : Enum<*>> enum(): StoredPropertyBase<T?> = doEnum(null, T::class.java)

  @PublishedApi
  internal fun <T : Enum<*>> doEnum(defaultValue: T? = null, clazz: Class<T>): StoredPropertyBase<T?> = addProperty(factory.enum(defaultValue, clazz))

  /**
   * Not-null list. Initialized as SmartList.
   */
  protected fun <T : Any> list(): StoredPropertyBase<MutableList<T>> = addProperty(factory.list<T>())

  protected fun <K : Any, V: Any> map(): StoredPropertyBase<MutableMap<K, V>> = addProperty(factory.map<K, V>(null))

  protected fun <K : Any, V: Any> linkedMap(): StoredPropertyBase<MutableMap<K, V>> = addProperty(factory.map<K, V>(LinkedHashMap()))

  @Deprecated(level = DeprecationLevel.ERROR, message = "Use map", replaceWith = ReplaceWith("map()"))
  protected fun <K : Any, V: Any> map(value: MutableMap<K, V>): StoredPropertyBase<MutableMap<K, V>> = addProperty(factory.map(value))

  @Deprecated(level = DeprecationLevel.ERROR, message = "Use string", replaceWith = ReplaceWith("string(defaultValue)"))
  protected fun property(defaultValue: String?) = string(defaultValue)

  /**
   * Empty string is always normalized to null.
   */
  protected fun string(defaultValue: String? = null): StoredPropertyBase<String?> = addProperty(factory.string(defaultValue))

  protected fun property(defaultValue: Int = 0): StoredPropertyBase<Int> = addProperty(factory.int(defaultValue))

  protected fun property(defaultValue: Long = 0): StoredPropertyBase<Long> = addProperty(factory.long(defaultValue))

  protected fun property(defaultValue: Float = 0f, valueNormalizer: ((value: Float) -> Float)? = null): StoredPropertyBase<Float> {
    return addProperty(factory.float(defaultValue, valueNormalizer))
  }

  protected fun property(defaultValue: Boolean = false): StoredPropertyBase<Boolean> = addProperty(factory.bool(defaultValue))

  // reset on load state
  fun resetModificationCount() {
    ownModificationCount = 0
  }

  protected fun incrementModificationCount() {
    intIncrementModificationCount()
  }

  @ApiStatus.Internal
  fun intIncrementModificationCount() {
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

  fun isEqualToDefault(): Boolean = properties.all { it.isEqualToDefault() }

  /**
   * If you use [set], [treeSet] or [linkedMap], you must ensure that [incrementModificationCount] is called for each mutation operation on corresponding property value (e.g. add, remove).
   * Setting property to a new value updates modification count, but direct modification of mutable collection or map doesn't.
   *
   * [list] and [map] track content mutation, but if key or value is not primitive value, you also have to [incrementModificationCount] in case of nested mutation.
   */
  @Transient
  override fun getModificationCount(): Long {
    var result = ownModificationCount
    for (property in properties) {
      result += property.getModificationCount()
    }
    return result
  }

  override fun equals(other: Any?): Boolean = this === other || (other is BaseState && properties == other.properties)

  override fun hashCode(): Int = properties.hashCode()

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

  @JvmOverloads
  fun copyFrom(state: BaseState, isMustBeTheSameType: Boolean = true) {
    val propertyCount = state.properties.size
    if (isMustBeTheSameType) {
      LOG.assertTrue(propertyCount == properties.size)
    }

    var changed = false
    for ((index, otherProperty) in state.properties.withIndex()) {
      val property = properties.get(index)
      LOG.assertTrue(otherProperty.name == property.name)
      if (property.setValue(otherProperty)) {
        changed = true
      }
    }

    if (changed) {
      incrementModificationCount()
    }
  }

  // internal usage only
  @Suppress("FunctionName")
  @ApiStatus.Internal
  fun __getProperties() = properties
}

interface StatePropertyFactory {
  fun bool(defaultValue: Boolean): StoredPropertyBase<Boolean>

  fun <T> obj(defaultValue: T): StoredPropertyBase<T>

  fun <T> obj(initialValue: T, isDefault: (value: T) -> Boolean): StoredPropertyBase<T>

  fun <T : BaseState?> stateObject(initialValue: T): StoredPropertyBase<T>

  fun <T : Any> list(): StoredPropertyBase<MutableList<T>>

  fun <K : Any, V: Any> map(value: MutableMap<K, V>?): StoredPropertyBase<MutableMap<K, V>>

  fun float(defaultValue: Float = 0f, valueNormalizer: ((value: Float) -> Float)? = null): StoredPropertyBase<Float>

  fun long(defaultValue: Long): StoredPropertyBase<Long>

  fun int(defaultValue: Int): StoredPropertyBase<Int>

  // nullable default value is not a default value
  fun stringSet(defaultValue: String?): StoredPropertyBase<MutableSet<String>>

  fun <E> treeSet(): StoredPropertyBase<MutableSet<E>> where E : Comparable<E>, E : BaseState

  fun <T : Enum<*>> enum(defaultValue: T?, clazz: Class<T>): StoredPropertyBase<T?>

  fun string(defaultValue: String?): StoredPropertyBase<String?>
}