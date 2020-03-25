// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

open class EntitiesBarrel private constructor(
  entities: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>>
) : Iterable<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {

  constructor() : this(emptyMap())

  protected open val entitiesByType: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>> = HashMap(entities)

  @Suppress("UNCHECKED_CAST")
  open operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entitiesByType[clazz] as EntityFamily<T>?

  fun all() = entitiesByType

  override fun iterator(): Iterator<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {
    return entitiesByType.iterator()
  }

  fun copy(): EntitiesBarrel = EntitiesBarrel(
    this.entitiesByType)
  fun join(other: EntitiesBarrel): EntitiesBarrel = EntitiesBarrel(
    entitiesByType + other.entitiesByType)
}

class MutableEntitiesBarrel : EntitiesBarrel() {
  override val entitiesByType: MutableMap<Class<out TypedEntity>, MutableEntityFamily<out TypedEntity>> = mutableMapOf()

  @Suppress("UNCHECKED_CAST")
  override operator fun <T : TypedEntity> get(clazz: Class<T>): MutableEntityFamily<T>? = entitiesByType[clazz] as MutableEntityFamily<T>?

  fun clear() = entitiesByType.clear()

  fun isEmpty() = entitiesByType.isEmpty()

  operator fun <T : TypedEntity> set(clazz: Class<T>, newFamily: MutableEntityFamily<T>) {
    entitiesByType[clazz] = newFamily
  }
}