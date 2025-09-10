// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.components

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.serialization.PropertyAccessor
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.invoke.VarHandle
import java.nio.charset.Charset

internal val LOG: Logger = logger<BaseState>()

private val factory: StatePropertyFactory = run {
  val implClass = BaseState::class.java.classLoader.loadClass("com.intellij.serialization.stateProperties.StatePropertyFactoryImpl")
  MethodHandles.lookup().findConstructor(implClass, MethodType.methodType(Void.TYPE)).invoke() as StatePropertyFactory
}

abstract class BaseState : SerializationFilter, ModificationTracker {
  companion object {
    @JvmStatic
    // should be part of class and not file level to access private field of class
    private val OWN_MODIFICATION_COUNT_HANDLE: VarHandle = MethodHandles
          .privateLookupIn(BaseState::class.java, MethodHandles.lookup())
          .findVarHandle(BaseState::class.java, "ownModificationCount", Long::class.java)
  }

  // do not use SmartList because most objects have more than 1 property
  private val properties: MutableList<StoredProperty<Any>> = ArrayList()

  @Volatile
  @Transient
  private var ownModificationCount: Long = 0L

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
  protected fun <T> property(initialValue: T, isDefault: (value: T) -> Boolean): StoredPropertyBase<T> = addProperty(factory.obj(initialValue, isDefault))

  /**
   * Collection considered as default if empty.
   * It is *YOUR* responsibility to call [incrementModificationCount] on incremental collection modifications.
   */
  protected fun stringSet(): StoredPropertyBase<MutableSet<String>> = addProperty(factory.stringSet(null))

  /**
   * Collection considered as default if it contains only the specified default value.
   * It is *YOUR* responsibility to call [incrementModificationCount] on incremental collection modifications.
   */
  protected fun stringSet(defaultValue: String): StoredPropertyBase<MutableSet<String>> = addProperty(factory.stringSet(defaultValue))

  /**
   * Collection considered as default if empty.
   * It is *YOUR* responsibility to call [incrementModificationCount] on incremental collection modifications.
   */
  protected fun <E> treeSet(): StoredPropertyBase<MutableSet<E>> where E : Comparable<E>, E : BaseState = addProperty(factory.treeSet())

  /**
   * Charset is immutable, so, it is safe to use it as the default value.
   */
  protected fun <T : Charset> property(initialValue: T): StoredPropertyBase<T> = addProperty(factory.obj(initialValue))

  protected inline fun <reified T : Enum<*>> enum(defaultValue: T): StoredPropertyBase<T> {
    @Suppress("UNCHECKED_CAST")
    return doEnum(defaultValue, T::class.java) as StoredPropertyBase<T>
  }

  protected inline fun <reified T : Enum<*>> enum(): StoredPropertyBase<T?> = doEnum(null, T::class.java)

  @PublishedApi
  internal fun <T : Enum<*>> doEnum(defaultValue: T? = null, clazz: Class<T>): StoredPropertyBase<T?> = addProperty(
    factory.enum(defaultValue, clazz))

  /**
   * Not-null list. Initialized as SmartList.
   * It is *YOUR* responsibility to call [incrementModificationCount] if stored values are not immutable.
   */
  protected fun <T : Any> list(): StoredPropertyBase<MutableList<T>> = addProperty(factory.list())

  /**
   * It is *YOUR* responsibility to call [incrementModificationCount] if stored values are not immutable.
   */
  protected fun <K : Any, V : Any> map(): StoredPropertyBase<MutableMap<K, V>> = addProperty(factory.map(null))

  /**
   * It is *YOUR* responsibility to call [incrementModificationCount] on incremental collection modifications.
   */
  protected fun <K : Any, V : Any> linkedMap(): StoredPropertyBase<MutableMap<K, V>> = addProperty(factory.map(LinkedHashMap()))

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

  /**
   * If you use [set], [treeSet] or [linkedMap] you must ensure that [incrementModificationCount] is called for each
   * mutation operation on corresponding property value (e.g., add, remove, put).
   *
   * [list] and [map] track content mutation, but if key or value is mutable, you have to call [incrementModificationCount].
   *
   * You can set property value to a new collection.
   * In this case, the underlying collection will be cleared and filled with an assigned collection's contents.
   * It will update the modification count.
   */
  protected fun incrementModificationCount() {
    intIncrementModificationCount()
  }

  @ApiStatus.Internal
  fun intIncrementModificationCount() {
    addModificationCount(1L)
  }

  @ApiStatus.Internal
  fun addModificationCount(delta: Long) {
    OWN_MODIFICATION_COUNT_HANDLE.getAndAdd(this, delta)
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

  @ApiStatus.Internal
  fun isEqualToDefault(): Boolean = properties.all { it.isEqualToDefault() }

  /**
   * See [incrementModificationCount]
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

  override fun toString(): String = properties.joinToString(" ")

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
  fun __getProperties(): MutableList<StoredProperty<Any>> = properties
}

@ApiStatus.Internal
interface StatePropertyFactory {
  fun bool(defaultValue: Boolean): StoredPropertyBase<Boolean>

  fun <T> obj(defaultValue: T): StoredPropertyBase<T>

  fun <T> obj(initialValue: T, isDefault: (value: T) -> Boolean): StoredPropertyBase<T>

  fun <T : BaseState?> stateObject(initialValue: T): StoredPropertyBase<T>

  fun <T : Any> list(): StoredPropertyBase<MutableList<T>>

  fun <K : Any, V : Any> map(value: MutableMap<K, V>?): StoredPropertyBase<MutableMap<K, V>>

  fun float(defaultValue: Float = 0f, valueNormalizer: ((value: Float) -> Float)? = null): StoredPropertyBase<Float>

  fun long(defaultValue: Long): StoredPropertyBase<Long>

  fun int(defaultValue: Int): StoredPropertyBase<Int>

  // nullable default value is not a default value
  fun stringSet(defaultValue: String?): StoredPropertyBase<MutableSet<String>>

  fun <E> treeSet(): StoredPropertyBase<MutableSet<E>> where E : Comparable<E>, E : BaseState

  fun <T : Enum<*>> enum(defaultValue: T?, clazz: Class<T>): StoredPropertyBase<T?>

  fun string(defaultValue: String?): StoredPropertyBase<String?>
}