// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.workspaceModel.storage.GeneratedCodeCompatibilityChecker
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithSymbolicId

internal open class ImmutableEntitiesBarrel internal constructor(
  override val entityFamilies: List<ImmutableEntityFamily<out WorkspaceEntity>?>
) : EntitiesBarrel() {
  constructor(): this(emptyList())
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
  }

  fun getEntityDataForModification(id: EntityId): WorkspaceEntityData<*> {
    return getMutableEntityFamily(id.clazz).getEntityDataForModification(id.arrayId)
  }

  fun getEntityDataForModificationOrNull(id: EntityId): WorkspaceEntityData<*>? {
    return getMutableEntityFamily(id.clazz).getEntityDataForModificationOrNull(id.arrayId)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> add(newEntity: WorkspaceEntityData<T>, clazz: Int) {
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(newEntity)
  }

  fun book(clazz: Int): EntityId {
    val arrayId = getMutableEntityFamily(clazz).book()
    return createEntityId(arrayId, clazz)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> cloneAndAdd(newEntity: WorkspaceEntityData<T>, clazz: Int): WorkspaceEntityData<T> {
    val cloned = newEntity.clone()
    (getMutableEntityFamily(clazz) as MutableEntityFamily<T>).add(cloned)
    return cloned
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> cloneAndAddAt(newEntity: WorkspaceEntityData<T>, entityId: EntityId): WorkspaceEntityData<T> {
    val cloned = newEntity.clone()
    cloned.id = entityId.arrayId
    (getMutableEntityFamily(entityId.clazz) as MutableEntityFamily<T>).insertAtId(cloned)
    return cloned
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> replaceById(newEntity: WorkspaceEntityData<T>, clazz: Int) {
    val family = getMutableEntityFamily(clazz) as MutableEntityFamily<T>
    if (!family.exists(newEntity.id)) {
      thisLogger().error("Nothing to replace. Class: $clazz, new entity: $newEntity")
      return
    }
    family.replaceById(newEntity)
  }

  fun toImmutable(): ImmutableEntitiesBarrel {
    val friezedEntities = entityFamilies.map { family ->
      when (family) {
        is MutableEntityFamily<*> -> if (!family.isEmpty()) family.toImmutable() else null
        is ImmutableEntityFamily<*> -> family
        else -> null
      }
    }
    return ImmutableEntitiesBarrel(friezedEntities)
  }

  private fun getMutableEntityFamily(unmodifiableEntityId: Int): MutableEntityFamily<*> {
    fillEmptyFamilies(unmodifiableEntityId)

    val entityFamily = entityFamilies[unmodifiableEntityId] ?: run {
      GeneratedCodeCompatibilityChecker.checkCode(unmodifiableEntityId.findWorkspaceEntity())
      val emptyEntityFamily = MutableEntityFamily.createEmptyMutable<WorkspaceEntity>()
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

  fun exists(entityId: EntityId): Boolean {
    return get(entityId.clazz)?.exists(entityId.arrayId) ?: false
  }

  open operator fun get(clazz: Int): EntityFamily<out WorkspaceEntity>? = entityFamilies.getOrNull(clazz)

  fun size() = entityFamilies.size

  fun assertConsistency(abstractEntityStorage: AbstractEntityStorage) {
    val symbolicIds = HashSet<SymbolicEntityId<*>>()
    entityFamilies.forEachIndexed { i, family ->
      if (family == null) return@forEachIndexed
      val clazz = i.findEntityClass<WorkspaceEntity>()
      val hasSymbolicId = WorkspaceEntityWithSymbolicId::class.java.isAssignableFrom(clazz)
      family.assertConsistency { entityData ->
        // Assert correctness of the class
        val immutableClass = entityData.getEntityInterface()
        assert(clazz == immutableClass) {
          """EntityFamily contains entity data of wrong type:
                | - EntityFamily class:   $clazz
                | - entityData class:     $immutableClass
              """.trimMargin()
        }

        // Assert unique of persistent id
        if (hasSymbolicId) {
          val symbolicId = entityData.symbolicId()
          assert(symbolicId != null) { "Symbolic id expected for $clazz" }
          assert(symbolicId !in symbolicIds) { "Duplicated symbolic ids: $symbolicId" }
          symbolicIds.add(symbolicId!!)
        }

        if (entityData is WithAssertableConsistency) {
          entityData.assertConsistency(abstractEntityStorage)
        }
      }
    }
  }
}
