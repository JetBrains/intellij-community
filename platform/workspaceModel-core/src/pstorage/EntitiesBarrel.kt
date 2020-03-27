// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

open class EntitiesBarrel internal constructor(
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

  fun copy(): EntitiesBarrel = EntitiesBarrel(this.entitiesByType)

  fun join(other: EntitiesBarrel): EntitiesBarrel = EntitiesBarrel(entitiesByType + other.entitiesByType)
}

class MutableEntitiesBarrel : EntitiesBarrel() {
  override val entitiesByType: MutableMap<Class<out TypedEntity>, EntityFamily<out TypedEntity>> = mutableMapOf()

  override operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entitiesByType[clazz] as EntityFamily<T>?

  fun clear() = entitiesByType.clear()

  fun isEmpty() = entitiesByType.isEmpty()

  operator fun <T : TypedEntity> set(clazz: Class<T>, newFamily: MutableEntityFamily<T>) {
    entitiesByType[clazz] = newFamily
  }

  fun toImmutable(): EntitiesBarrel {
    entitiesByType.forEach { (_, family) ->
      if (family is MutableEntityFamily<*>) family.freeze()
    }
    return EntitiesBarrel(HashMap(entitiesByType))
  }

  companion object {
    fun from(original: EntitiesBarrel): MutableEntitiesBarrel {
      val res = MutableEntitiesBarrel()
      res.entitiesByType.putAll(original.all())
      return res
    }
  }
}