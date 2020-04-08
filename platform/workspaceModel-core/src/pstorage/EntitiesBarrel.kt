// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

internal open class EntitiesBarrel internal constructor(
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

  fun assertConsistency() {
    entitiesByType.forEach { (clazz, family) ->
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

internal class MutableEntitiesBarrel : EntitiesBarrel() {
  override val entitiesByType: MutableMap<Class<out TypedEntity>, EntityFamily<out TypedEntity>> = mutableMapOf()

  override operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entitiesByType[clazz] as EntityFamily<T>?

  fun clear() = entitiesByType.clear()

  fun isEmpty() = entitiesByType.isEmpty()

  operator fun <T : TypedEntity> set(clazz: Class<T>, newFamily: MutableEntityFamily<T>) {
    entitiesByType[clazz] = newFamily
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

  fun <T : TypedEntity> replaceById(newEntity: PEntityData<T>, clazz: Class<T>) {
    val family = getMutableEntityFamily(clazz)
    if (!family.exists(newEntity.id)) error("Nothing to replace")  // TODO: 25.03.2020 Or just call "add"?
    family.replaceById(newEntity)
  }

  fun toImmutable(): EntitiesBarrel {
    entitiesByType.forEach { (_, family) ->
      if (family is MutableEntityFamily<*>) family.freeze()
    }
    return EntitiesBarrel(HashMap(entitiesByType))
  }

  private fun <T : TypedEntity> getMutableEntityFamily(unmodifiableEntityClass: Class<T>): MutableEntityFamily<T> {
    val entityFamily = entitiesByType[unmodifiableEntityClass] as EntityFamily<T>?
    if (entityFamily == null) {
      val newMutable = MutableEntityFamily.createEmptyMutable<T>()
      entitiesByType[unmodifiableEntityClass] = newMutable
      return newMutable
    }
    else {
      if (entityFamily !is MutableEntityFamily<T> || !entityFamily.familyCopiedToModify) {
        val newMutable = entityFamily.copyToMutable()
        entitiesByType[unmodifiableEntityClass] = newMutable
        return newMutable
      }
      else {
        return entityFamily
      }
    }
  }

  companion object {
    fun from(original: EntitiesBarrel): MutableEntitiesBarrel {
      val res = MutableEntitiesBarrel()
      res.entitiesByType.putAll(original.all())
      return res
    }
  }
}