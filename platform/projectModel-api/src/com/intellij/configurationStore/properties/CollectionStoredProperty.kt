/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.configurationStore.properties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import com.intellij.util.SmartList
import kotlin.reflect.KProperty

/**
 * AbstractCollectionBinding modifies collection directly, so, we cannot use null as default null and return empty list on get.
 */
internal open class CollectionStoredProperty<E, C : MutableCollection<E>>(protected val value: C) : StoredPropertyBase<C>() {
  override fun isEqualToDefault() = value.isEmpty()

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: C) {
    if (doSetValue(value, newValue)) {
      thisRef.intIncrementModificationCount()
    }
  }

  protected fun doSetValue(old: C, new: C): Boolean {
    if (old == new) {
      return false
    }

    old.clear()
    old.addAll(new)
    return true
  }

  override fun equals(other: Any?) = this === other || (other is CollectionStoredProperty<*, *> && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = "$name = ${if (isEqualToDefault()) "" else value.joinToString(" ")}"

  override fun setValue(other: StoredProperty): Boolean {
    @Suppress("UNCHECKED_CAST")
    return doSetValue(value, (other as CollectionStoredProperty<E, C>).value)
  }
}

internal class ListStoredProperty<T> : CollectionStoredProperty<T, SmartList<T>>(SmartList()) {
  override fun getModificationCount() = value.modificationCount.toLong()
}

internal class MapStoredProperty<K: Any, V>(private val value: MutableMap<K, V>) : StoredPropertyBase<MutableMap<K, V>>() {
  override fun isEqualToDefault() = value.isEmpty()

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: MutableMap<K, V>) {
    if (doSetValue(value, newValue)) {
      thisRef.intIncrementModificationCount()
    }
  }

  private fun doSetValue(old: MutableMap<K, V>, new: Map<K, V>): Boolean {
    if (old == new) {
      return false
    }

    old.clear()
    old.putAll(new)
    return true
  }

  override fun equals(other: Any?) = this === other || (other is MapStoredProperty<*, *> && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (isEqualToDefault()) "" else value.toString()

  override fun setValue(other: StoredProperty): Boolean {
    @Suppress("UNCHECKED_CAST")
    return doSetValue(value, (other as MapStoredProperty<K, V>).value)
  }
}
