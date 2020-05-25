// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.TypedEntity
import org.jetbrains.annotations.TestOnly

internal open class ImmutableEntitiesBarrel internal constructor(
  override val entities: List<ImmutableEntityFamily<out TypedEntity>?>
) : EntitiesBarrel() {
  fun assertConsistency() {
    entities.forEachIndexed { i, family ->
      val clazz = i.findEntityClass<TypedEntity>()
      family?.assertConsistency { entityData ->
        val immutableClass = ClassConversion.entityDataToEntity(entityData.javaClass)
        assert(clazz == immutableClass) {
          """EntityFamily contains entity data of wrong type:
            | - EntityFamily class:   $clazz
            | - entityData class:     $immutableClass
          """.trimMargin()
        }
      }
    }
  }

  companion object {
    val EMPTY = ImmutableEntitiesBarrel(emptyList())
  }
}

internal class MutableEntitiesBarrel private constructor(
  override var entities: MutableList<EntityFamily<out TypedEntity>?>
) : EntitiesBarrel() {
  fun remove(id: Int, clazz: Int) {
    val entityFamily = getMutableEntityFamily(clazz)
    entityFamily.remove(id)
    if (entityFamily.isEmpty()) entities[clazz] = null
  }

  fun getEntityDataForModification(id: PId): PEntityData<*> {
    return getMutableEntityFamily(id.clazz).getEntityDataForModification(id.arrayId)
  }

  fun <T : TypedEntity> add(newEntity: PEntityData<T>, clazz: Int) {
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(newEntity)
  }

  fun <T : TypedEntity> cloneAndAdd(newEntity: PEntityData<T>, clazz: Int): PEntityData<T> {
    val cloned = newEntity.clone()
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(cloned)
    return cloned
  }

  fun <T : TypedEntity> replaceById(newEntity: PEntityData<T>, clazz: Int) {
    val family = getMutableEntityFamily(clazz) as MutableEntityFamily<T>
    if (!family.exists(newEntity.id)) error("Nothing to replace")
    family.replaceById(newEntity)
  }

  fun toImmutable(): ImmutableEntitiesBarrel {
    val friezedEntities = entities.map { family ->
      when (family) {
        is MutableEntityFamily<*> -> family.toImmutable()
        is ImmutableEntityFamily<*> -> family
        else -> null
      }
    }
    return ImmutableEntitiesBarrel(friezedEntities)
  }

  private fun getMutableEntityFamily(unmodifiableEntityId: Int): MutableEntityFamily<*> {
    while (entities.size <= unmodifiableEntityId) {
      entities.add(MutableEntityFamily.createEmptyMutable())
    }
    val entityFamily = entities[unmodifiableEntityId] ?: run {
      val emptyEntityFamily = MutableEntityFamily.createEmptyMutable()
      entities[unmodifiableEntityId] = emptyEntityFamily
      emptyEntityFamily
    }
    return when (entityFamily) {
      is MutableEntityFamily<*> -> entityFamily
      is ImmutableEntityFamily<*> -> {
        val newMutable = entityFamily.toMutable()
        entities[unmodifiableEntityId] = newMutable
        newMutable
      }
    }
  }

  companion object {
    fun from(original: ImmutableEntitiesBarrel): MutableEntitiesBarrel = MutableEntitiesBarrel(ArrayList(original.allEntities()))
  }
}

internal sealed class EntitiesBarrel {
  protected abstract val entities: List<EntityFamily<out TypedEntity>?>

  open operator fun get(clazz: Int): EntityFamily<out TypedEntity>? = entities.getOrNull(clazz)

  internal fun allEntities() = entities.filterNotNull()
}
