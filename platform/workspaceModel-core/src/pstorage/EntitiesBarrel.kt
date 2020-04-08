// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

internal open class EntitiesBarrel internal constructor(
  override val entities: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>>
) : AbstractEntitiesBarrel() {

  constructor() : this(emptyMap())

  fun copy(): EntitiesBarrel = EntitiesBarrel(HashMap(this.entities))

  fun join(other: EntitiesBarrel): EntitiesBarrel = EntitiesBarrel(entities + other.entities)

  fun assertConsistency() {
    entities.forEach { (clazz, family) ->
      family.assertConsistency { entityData ->
        val immutableClass = ClassConversion.entityDataToEntity(entityData::class)
        assert(clazz.kotlin ==  immutableClass) {
          """EntityFamily contains entity data of wrong type:
            | - EntityFamily class:   $clazz
            | - entityData class:     $immutableClass
          """.trimMargin()
        }
      }
    }
  }
}

internal class MutableEntitiesBarrel(
  override val entities: MutableMap<Class<out TypedEntity>, AbstractEntityFamily<out TypedEntity>>
) : AbstractEntitiesBarrel() {

  constructor() : this(HashMap())

  fun clear() = entities.clear()

  fun isEmpty() = entities.isEmpty()

  operator fun <T : TypedEntity> set(clazz: Class<T>, newFamily: MutableEntityFamily<T>) {
    entities[clazz] = newFamily
  }

  fun remove(id: Int, clazz: Class<out TypedEntity>) {
    getMutableEntityFamily(clazz).remove(id)
  }

  fun <E : TypedEntity> getEntityDataForModification(id: PId<E>): PEntityData<E> {
    return getMutableEntityFamily(id.clazz.java).getEntityDataForModification(id)
  }

  fun <T : TypedEntity> add(newEntity: PEntityData<T>, clazz: Class<T>) {
    getMutableEntityFamily(clazz).add(newEntity)
  }

  fun <T : TypedEntity> cloneAndAdd(newEntity: PEntityData<T>, clazz: Class<T>): PEntityData<T> {
    val cloned = newEntity.clone()
    getMutableEntityFamily(clazz).add(cloned)
    return cloned
  }

  fun <T : TypedEntity> replaceById(newEntity: PEntityData<T>, clazz: Class<T>) {
    val family = getMutableEntityFamily(clazz)
    if (!family.exists(newEntity.id)) error("Nothing to replace")  // TODO: 25.03.2020 Or just call "add"?
    family.replaceById(newEntity)
  }

  fun toImmutable(): EntitiesBarrel {
    val friezedEntities = entities.mapValues { (_, family) ->
      when (family) {
        is MutableEntityFamily<*> -> family.toImmutable()
        is EntityFamily<*> -> family
      }
    }
    return EntitiesBarrel(friezedEntities)
  }

  private fun <T : TypedEntity> getMutableEntityFamily(unmodifiableEntityClass: Class<T>): MutableEntityFamily<T> {
    val entityFamily = entities[unmodifiableEntityClass] as AbstractEntityFamily<T>?
    if (entityFamily == null) {
      val newMutable = MutableEntityFamily.createEmptyMutable<T>()
      entities[unmodifiableEntityClass] = newMutable
      return newMutable
    }
    else {
      return when (entityFamily) {
        is MutableEntityFamily<T> -> entityFamily
        is EntityFamily<T> -> {
          val newMutable = entityFamily.copyToMutable()
          entities[unmodifiableEntityClass] = newMutable
          newMutable
        }
      }
    }
  }

  companion object {
    fun from(original: EntitiesBarrel): MutableEntitiesBarrel = MutableEntitiesBarrel(HashMap(original.all()))
  }
}

internal sealed class AbstractEntitiesBarrel : Iterable<Map.Entry<Class<out TypedEntity>, AbstractEntityFamily<out TypedEntity>>> {

  protected abstract val entities: Map<Class<out TypedEntity>, AbstractEntityFamily<out TypedEntity>>

  @Suppress("UNCHECKED_CAST")
  open operator fun <T : TypedEntity> get(clazz: Class<T>): AbstractEntityFamily<T>? = entities[clazz] as AbstractEntityFamily<T>?

  fun all() = entities

  override fun iterator(): Iterator<Map.Entry<Class<out TypedEntity>, AbstractEntityFamily<out TypedEntity>>> = entities.iterator()
}
