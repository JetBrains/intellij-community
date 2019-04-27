// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.properties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.JsonSchemaType
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import com.intellij.util.SmartList
import kotlin.reflect.KProperty

// Technically, it is not possible to proxy write operations because collection/map can be mutated via iterator.
// So, even if Kotlin can create delegator for us, still, to track mutations via iterator we have to reimplement collection/map.

/**
 * AbstractCollectionBinding modifies collection directly, so, we cannot use null as default null and return empty list on get.
 */
open class CollectionStoredProperty<E, C : MutableCollection<E>>(protected val value: C) : StoredPropertyBase<C>() {
  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.ARRAY

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

  override fun setValue(other: StoredProperty<C>): Boolean {
    @Suppress("UNCHECKED_CAST")
    return doSetValue(value, (other as CollectionStoredProperty<E, C>).value)
  }

  @Suppress("FunctionName")
  fun __getValue() = value
}

internal class ListStoredProperty<T> : CollectionStoredProperty<T, SmartList<T>>(SmartList()) {
  override fun getModificationCount() = value.modificationCount.toLong()
}