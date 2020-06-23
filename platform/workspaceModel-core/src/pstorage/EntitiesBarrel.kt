// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import org.jetbrains.annotations.TestOnly

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
  fun remove(id: Int, clazz: Class<out TypedEntity>) {
    val entityFamily = getMutableEntityFamily(clazz)
    entityFamily.remove(id)
    if (entityFamily.isEmpty()) entities.remove(clazz)
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
    val friezedEntities = entities.mapValues { (_, family) ->
      when (family) {
        is MutableEntityFamily<*> -> family.toImmutable()
        is ImmutableEntityFamily<*> -> family
      }
    }
    return ImmutableEntitiesBarrel(friezedEntities)
  }

  fun isEmpty() = entities.isEmpty()
  fun clear() = entities.clear()

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
    fun from(original: ImmutableEntitiesBarrel): MutableEntitiesBarrel {
      val copy = HashMap<Class<out TypedEntity>, EntityFamily<out TypedEntity>>()
      original.forEach { entry -> copy[entry.key] = entry.value }
      return MutableEntitiesBarrel(copy)
    }
  }
}

internal sealed class EntitiesBarrel : Iterable<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> {
  protected abstract val entities: Map<Class<out TypedEntity>, EntityFamily<out TypedEntity>>

  @Suppress("UNCHECKED_CAST")
  open operator fun <T : TypedEntity> get(clazz: Class<T>): EntityFamily<T>? = entities[clazz] as EntityFamily<T>?
  override fun iterator(): Iterator<Map.Entry<Class<out TypedEntity>, EntityFamily<out TypedEntity>>> = entities.iterator()
  @TestOnly
  internal fun all() = entities
}
