// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId

internal open class ImmutableEntitiesBarrel internal constructor(
  override val entityFamilies: List<ImmutableEntityFamily<out WorkspaceEntity>?>
) : EntitiesBarrel() {
  companion object {
    val EMPTY = ImmutableEntitiesBarrel(emptyList())
  }
}

internal class MutableEntitiesBarrel private constructor(
  override var entityFamilies: MutableList<EntityFamily<out WorkspaceEntity>?>
) : EntitiesBarrel() {
  fun remove(id: Int, clazz: Int) {
    val entityFamily = getMutableEntityFamily(clazz)
    entityFamily.remove(id)
    if (entityFamily.isEmpty()) entityFamilies[clazz] = null
  }

  fun getEntityDataForModification(id: EntityId): WorkspaceEntityData<*> {
    return getMutableEntityFamily(id.clazz).getEntityDataForModification(id.arrayId)
  }

  fun <T : WorkspaceEntity> add(newEntity: WorkspaceEntityData<T>, clazz: Int) {
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(newEntity)
  }

  fun <T : WorkspaceEntity> cloneAndAdd(newEntity: WorkspaceEntityData<T>, clazz: Int): WorkspaceEntityData<T> {
    val cloned = newEntity.clone()
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(cloned)
    return cloned
  }

  fun <T : WorkspaceEntity> replaceById(newEntity: WorkspaceEntityData<T>, clazz: Int) {
    val family = getMutableEntityFamily(clazz) as MutableEntityFamily<T>
    if (!family.exists(newEntity.id)) error("Nothing to replace")
    family.replaceById(newEntity)
  }

  fun toImmutable(): ImmutableEntitiesBarrel {
    val friezedEntities = entityFamilies.map { family ->
      when (family) {
        is MutableEntityFamily<*> -> family.toImmutable()
        is ImmutableEntityFamily<*> -> family
        else -> null
      }
    }
    return ImmutableEntitiesBarrel(friezedEntities)
  }

  private fun getMutableEntityFamily(unmodifiableEntityId: Int): MutableEntityFamily<*> {
    fillEmptyFamilies(unmodifiableEntityId)

    val entityFamily = entityFamilies[unmodifiableEntityId] ?: run {
      val emptyEntityFamily = MutableEntityFamily.createEmptyMutable()
      entityFamilies[unmodifiableEntityId] = emptyEntityFamily
      emptyEntityFamily
    }
    return when (entityFamily) {
      is MutableEntityFamily<*> -> entityFamily
      is ImmutableEntityFamily<*> -> {
        val newMutable = entityFamily.toMutable()
        entityFamilies[unmodifiableEntityId] = newMutable
        newMutable
      }
    }
  }

  internal fun fillEmptyFamilies(unmodifiableEntityId: Int) {
    while (entityFamilies.size <= unmodifiableEntityId) entityFamilies.add(null)
  }

  companion object {
    fun from(original: ImmutableEntitiesBarrel): MutableEntitiesBarrel = MutableEntitiesBarrel(ArrayList(original.entityFamilies))
    fun create() = MutableEntitiesBarrel(ArrayList())
  }
}

internal sealed class EntitiesBarrel {
  internal abstract val entityFamilies: List<EntityFamily<out WorkspaceEntity>?>

  open operator fun get(clazz: Int): EntityFamily<out WorkspaceEntity>? = entityFamilies.getOrNull(clazz)

  fun size() = entityFamilies.size

  fun assertConsistency() {
    val persistentIds = HashSet<PersistentEntityId<*>>()
    entityFamilies.forEachIndexed { i, family ->
      val clazz = i.findEntityClass<WorkspaceEntity>()
      val hasPersistentId = WorkspaceEntityWithPersistentId::class.java.isAssignableFrom(clazz)
      family?.assertConsistency { entityData ->
        // Assert correctness of the class
        val immutableClass = ClassConversion.entityDataToEntity(entityData.javaClass)
        assert(clazz == immutableClass) {
          """EntityFamily contains entity data of wrong type:
            | - EntityFamily class:   $clazz
            | - entityData class:     $immutableClass
          """.trimMargin()
        }

        // Assert unique of persistent id
        if (hasPersistentId) {
          val persistentId = entityData.persistentId(WorkspaceEntityStorageImpl.EMPTY)
          assert(persistentId != null) { "Persistent id expected for $clazz" }
          assert(persistentId !in persistentIds) { "Duplicated persistent ids: $persistentId" }
          persistentIds.add(persistentId!!)
        }
      }
    }
  }
}
