// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity

internal open class ImmutableEntitiesBarrel internal constructor(
  override val entities: Map<Class<out TypedEntity>, ImmutableEntityFamily<out TypedEntity>>
) : EntitiesBarrel() {
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

  companion object {
    val EMPTY = ImmutableEntitiesBarrel(emptyMap())
  }
}

internal class MutableEntitiesBarrel(
  override var entities: MutableMap<Class<out TypedEntity>, EntityFamily<out TypedEntity>>
) : EntitiesBarrel() {

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
    if (!family.exists(newEntity.id)) error("Nothing to replace")
    family.replaceById(newEntity)
  }

  fun toImmutable(): ImmutableEntitiesBarrel {
    entities
    val friezedEntities = entities.mapValues { (_, family) ->
      when (family) {
        is MutableEntityFamily<*> -> family.toImmutable()
        is ImmutableEntityFamily<*> -> family
      }
    }
    return ImmutableEntitiesBarrel(friezedEntities)
  }

  private fun <T : TypedEntity> getMutableEntityFamily(unmodifiableEntityClass: Class<T>): MutableEntityFamily<T> {
    val entityFamily = entities[unmodifiableEntityClass] as EntityFamily<T>?
    if (entityFamily == null) {
      val newMutable = MutableEntityFamily.createEmptyMutable<T>()
      entities[unmodifiableEntityClass] = newMutable
      return newMutable
    }
    else {
      return when (entityFamily) {
        is MutableEntityFamily<T> -> entityFamily
        is ImmutableEntityFamily<T> -> {
          val newMutable = entityFamily.toMutable()
          entities[unmodifiableEntityClass] = newMutable
          newMutable
        }
      }
    }
  }

  companion object {
    fun from(original: ImmutableEntitiesBarrel): MutableEntitiesBarrel = MutableEntitiesBarrel(HashMap(original.all()))
  }
}

internal sealed class EntitiesBarrel : Iterable<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {

  protected abstract val entities: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>>

  @Suppress("UNCHECKED_CAST")
  open operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entities[clazz] as EntityFamily<T>?

  @Deprecated("This class is iterable")
  internal fun all() = entities

  override fun iterator(): Iterator<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> = entities.iterator()
}
