// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.copy
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex
import com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex

internal open class StorageIndexes(
  // List of IDs of entities that use this particular persistent id
  internal open val softLinks: MultimapStorageIndex<PersistentEntityId<*>>,
  internal open val virtualFileIndex: VirtualFileIndex,
  internal open val entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
  internal open val persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>,
  internal open val externalMappings: Map<String, ExternalEntityMappingImpl<*>>
) {

  constructor(softLinks: MultimapStorageIndex<PersistentEntityId<*>>,
              virtualFileIndex: VirtualFileIndex,
              entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
              persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>
  ) : this(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, emptyMap())

  companion object {
    val EMPTY = StorageIndexes(MultimapStorageIndex(), VirtualFileIndex(), EntityStorageInternalIndex(), EntityStorageInternalIndex(),
                               HashMap())
  }

  fun toMutable(): MutableStorageIndexes {
    val copiedSoftLinks = MultimapStorageIndex.MutableMultimapStorageIndex.from(softLinks)
    val copiedVirtualFileIndex = VirtualFileIndex.MutableVirtualFileIndex.from(virtualFileIndex)
    val copiedEntitySourceIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(entitySourceIndex)
    val copiedPersistentIdIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(persistentIdIndex)
    val copiedExternalMappings = MutableExternalEntityMappingImpl.fromMap(externalMappings)
    return MutableStorageIndexes(copiedSoftLinks, copiedVirtualFileIndex, copiedEntitySourceIndex, copiedPersistentIdIndex,
                                 copiedExternalMappings)
  }

  fun assertConsistency(storage: AbstractEntityStorage) {
    // Assert entity source index
    val entitySourceIndexCopy = entitySourceIndex.index.copy()
    storage.entitiesByType.entityFamilies.filterNotNull().forEach { family ->
      family.entities.asSequence().filterNotNull().forEach { data ->
        val removed = entitySourceIndexCopy.remove(data.createPid(), data.entitySource)
        assert(removed) { "Entity $data isn't found in entity source index. Entity source: ${data.entitySource}, Id: ${data.createPid()}" }
      }
    }
    assert(entitySourceIndexCopy.isEmpty()) { "Entity source index has garbage: $entitySourceIndexCopy" }

    // Assert persistent id index
    val persistentIdIndexCopy = persistentIdIndex.index.copy()
    storage.entitiesByType.entityFamilies.filterNotNull().forEach { family ->
      family.entities.asSequence().filterNotNull().forEach { data ->
        val persistentId = data.persistentId(storage)
        if (persistentId != null) {
          val removed = persistentIdIndexCopy.remove(data.createPid(), persistentId)
          assert(removed) { "Entity $data isn't found in persistent id index" }
        }
      }
    }
    assert(persistentIdIndexCopy.isEmpty()) { "Persistent id index has garbage: $persistentIdIndexCopy" }

    // Assert soft links
    val softLinksCopy = softLinks.index.copy()
    storage.entitiesByType.entityFamilies.filterNotNull().forEach { family ->
      family.entities.asSequence().filterNotNull().forEach { data ->
        if (data is SoftLinkable) {
          val links = data.getLinks()
          for (link in links) {
            val pids = softLinksCopy.getKeys(link)
            assert(data.createPid() in pids) { "Entity $data isn't found in soft links" }
            softLinksCopy.remove(data.createPid(), link)
          }
        }
      }
    }
    assert(softLinksCopy.isEmpty) { "Soft links have garbage: $softLinksCopy" }
  }
}

internal class MutableStorageIndexes(
  override val softLinks: MultimapStorageIndex.MutableMultimapStorageIndex<PersistentEntityId<*>>,
  override val virtualFileIndex: VirtualFileIndex.MutableVirtualFileIndex,
  override val entitySourceIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<EntitySource>,
  override val persistentIdIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<PersistentEntityId<*>>,
  override val externalMappings: MutableMap<String, MutableExternalEntityMappingImpl<*>>
) : StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, externalMappings) {

  fun <T : WorkspaceEntity> entityAdded(entityData: WorkspaceEntityData<T>, builder: WorkspaceEntityStorageBuilderImpl) {
    val pid = entityData.createPid()

    // Update soft links index
    if (entityData is SoftLinkable) {
      for (link in entityData.getLinks()) {
        softLinks.index(pid, link)
      }
    }

    entitySourceIndex.index(pid, entityData.entitySource)

    entityData.persistentId(builder)?.let { persistentId ->
      persistentIdIndex.index(pid, persistentId)
    }
  }

  fun updateSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.index(pid, link)
    }
  }

  fun removeFromSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.remove(pid, link)
    }
  }

  fun updateIndices(oldEntityId: EntityId, newEntityId: EntityId, builder: AbstractEntityStorage) {
    builder.indexes.virtualFileIndex.getVirtualFilesPerProperty(oldEntityId)?.forEach {
      virtualFileIndex.index(newEntityId, it.second, listOf(it.first))
    }
    builder.indexes.entitySourceIndex.getEntryById(oldEntityId)?.also { entitySourceIndex.index(newEntityId, it) }
    builder.indexes.persistentIdIndex.getEntryById(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  fun removeFromIndices(entityId: EntityId) {
    virtualFileIndex.index(entityId)
    entitySourceIndex.index(entityId)
    persistentIdIndex.index(entityId)
    externalMappings.values.forEach { it.remove(entityId) }
  }

  fun <T : WorkspaceEntity> simpleUpdateSoftReferences(copiedData: WorkspaceEntityData<T>) {
    val pid = copiedData.createPid()
    if (copiedData is SoftLinkable) {
      val beforeSoftLinks = HashSet(this.softLinks.getEntriesById(pid))
      val afterSoftLinks = copiedData.getLinks()
      if (beforeSoftLinks != afterSoftLinks) {
        beforeSoftLinks.forEach { this.softLinks.remove(pid, it) }
        afterSoftLinks.forEach { this.softLinks.index(pid, it) }
      }
    }
  }

  fun applyExternalMappingChanges(diff: WorkspaceEntityStorageBuilderImpl,
                                  replaceMap: HashBiMap<EntityId, EntityId>) {
    externalMappings.keys.asSequence().filterNot { it in diff.indexes.externalMappings }.forEach {
      externalMappings.remove(it)
    }

    diff.indexes.externalMappings.keys.asSequence().filterNot { it in externalMappings.keys }.forEach {
      externalMappings[it] = MutableExternalEntityMappingImpl.from(diff.indexes.externalMappings[it]!!)
    }

    diff.indexes.externalMappings.forEach { (identifier, index) ->
      externalMappings[identifier]?.applyChanges(index, replaceMap)
    }
  }

  fun toImmutable(): StorageIndexes {
    val copiedLinks = this.softLinks.toImmutable()
    val newVirtualFileIndex = virtualFileIndex.toImmutable()
    val newEntitySourceIndex = entitySourceIndex.toImmutable()
    val newPersistentIdIndex = persistentIdIndex.toImmutable()
    val newExternalMappings = MutableExternalEntityMappingImpl.toImmutable(externalMappings)
    return StorageIndexes(copiedLinks, newVirtualFileIndex, newEntitySourceIndex, newPersistentIdIndex, newExternalMappings)
  }
}
