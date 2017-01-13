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

import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class BaseState : SimpleModificationTracker(), SerializationFilter {
  // if property value differs from default
  private val properties: MutableList<StoredProperty<*>> = SmartList()

  override fun accepts(accessor: Accessor, bean: Any): Boolean {
    for (property in properties) {
      if (property.name == accessor.name) {
        return property.value != property.defaultValue
      }
    }
    return false
  }

  fun <T : Any> storedProperty(defaultValue: T? = null): ReadWriteProperty<BaseState, T?> {
    val result = StoredProperty(defaultValue)
    properties.add(result)
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BaseState) return false

    return properties == other.properties
  }

  override fun hashCode() = properties.hashCode()
}

internal class StoredProperty<T>(internal val defaultValue: T?) : ReadWriteProperty<BaseState, T?> {
  internal var value = defaultValue
  internal var name: String? = null

  override operator fun getValue(thisRef: BaseState, property: KProperty<*>) = value

  @Suppress("UNCHECKED_CAST")
  override fun setValue(thisRef: BaseState, property: KProperty<*>, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: T?) {
    if (value != newValue) {
      thisRef.incModificationCount()

      name = property.name
      value = newValue
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StoredProperty<*>) return false

    if (value != other.value) return false
    return true
  }

  override fun hashCode() = value?.hashCode() ?: 0
}