// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.copy
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityIndexImpl
import com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex

internal open class StorageIndexes(
  // List of IDs of entities that use this particular persistent id
  internal open val softLinks: BidirectionalMultiMap<PersistentEntityId<*>, EntityId>,
  internal open val virtualFileIndex: VirtualFileIndex,
  internal open val entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
  internal open val persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>,
  internal open val externalIndices: Map<String, ExternalEntityIndexImpl<*>>
) {

  constructor(softLinks: BidirectionalMultiMap<PersistentEntityId<*>, EntityId>,
              virtualFileIndex: VirtualFileIndex,
              entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
              persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>
  ) : this(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, emptyMap())

  companion object {
    val EMPTY = StorageIndexes(BidirectionalMultiMap(), VirtualFileIndex(), EntityStorageInternalIndex(), EntityStorageInternalIndex(),
                               HashMap())
  }

  fun toMutable(): MutableStorageIndexes {
    val copiedSoftLinks = softLinks.copy()
    val copiedVirtualFileIndex = VirtualFileIndex.MutableVirtualFileIndex.from(virtualFileIndex)
    val copiedEntitySourceIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(entitySourceIndex)
    val copiedPersistentIdIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(persistentIdIndex)
    val copiedExternalIndices = ExternalEntityIndexImpl.MutableExternalEntityIndexImpl.fromMap(externalIndices)
    return MutableStorageIndexes(copiedSoftLinks, copiedVirtualFileIndex, copiedEntitySourceIndex, copiedPersistentIdIndex,
                                 copiedExternalIndices)
  }

  fun assertConsistency(storage: AbstractEntityStorage) {
    // Assert entity source index
    val entitySourceIndexCopy = entitySourceIndex.index.copy()
    storage.entitiesByType.entities.filterNotNull().forEach { family ->
      family.entities.asSequence().filterNotNull().forEach { data ->
        val removed = entitySourceIndexCopy.remove(data.createPid(), data.entitySource)
        assert(removed) { "Entity $data isn't found in entity source index" }
      }
    }
    assert(entitySourceIndexCopy.isEmpty()) { "Entity source index has garbage: $entitySourceIndexCopy" }

    // Assert persistent id index
    val persistentIdIndexCopy = persistentIdIndex.index.copy()
    storage.entitiesByType.entities.filterNotNull().forEach { family ->
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
    val softLinksCopy = softLinks.copy()
    storage.entitiesByType.entities.filterNotNull().forEach { family ->
      family.entities.asSequence().filterNotNull().forEach { data ->
        if (data is SoftLinkable) {
          val links = data.getLinks()
          for (link in links) {
            val pids = softLinksCopy.getValues(link)
            assert(data.createPid() in pids) { "Entity $data isn't found in soft links" }
            softLinksCopy.remove(link, data.createPid())
          }
        }
      }
    }
    assert(softLinksCopy.isEmpty) { "Soft links have garbage: $softLinksCopy" }
  }
}

internal class MutableStorageIndexes(
  override val softLinks: BidirectionalMultiMap<PersistentEntityId<*>, EntityId>,
  override val virtualFileIndex: VirtualFileIndex.MutableVirtualFileIndex,
  override val entitySourceIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<EntitySource>,
  override val persistentIdIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<PersistentEntityId<*>>,
  override val externalIndices: MutableMap<String, ExternalEntityIndexImpl.MutableExternalEntityIndexImpl<*>>
) : StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, externalIndices) {

  fun <T : WorkspaceEntity> entityAdded(entityData: WorkspaceEntityData<T>, builder: WorkspaceEntityStorageBuilderImpl) {
    val pid = entityData.createPid()

    // Update soft links index
    if (entityData is SoftLinkable) {
      for (link in entityData.getLinks()) {
        softLinks.put(link, pid)
      }
    }

    entitySourceIndex.index(pid, entityData.entitySource)

    entityData.persistentId(builder)?.let { persistentId ->
      if (persistentIdIndex.getIdsByEntry(persistentId) != null) error("Entity with persistentId: $persistentId already exist")
      persistentIdIndex.index(pid, persistentId)
    }
  }

  fun updateSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.put(link, pid)
    }
  }

  fun removeFromSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.remove(link, pid)
    }
  }

  fun updateIndices(oldEntityId: EntityId, newEntityId: EntityId, builder: AbstractEntityStorage) {
    builder.indexes.virtualFileIndex.getVirtualFiles(oldEntityId)?.forEach { virtualFileIndex.index(newEntityId, listOf(it)) }
    builder.indexes.entitySourceIndex.getEntryById(oldEntityId)?.also { entitySourceIndex.index(newEntityId, it) }
    builder.indexes.persistentIdIndex.getEntryById(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  fun removeFromIndices(entityId: EntityId) {
    virtualFileIndex.index(entityId)
    entitySourceIndex.index(entityId)
    persistentIdIndex.index(entityId)
  }

  fun <T : WorkspaceEntity> simpleUpdateSoftReferences(copiedData: WorkspaceEntityData<T>) {
    val pid = copiedData.createPid()
    if (copiedData is SoftLinkable) {
      val beforeSoftLinks = HashSet(this.softLinks.getKeys(pid))
      val afterSoftLinks = copiedData.getLinks()
      if (beforeSoftLinks != afterSoftLinks) {
        beforeSoftLinks.forEach { this.softLinks.remove(it, pid) }
        afterSoftLinks.forEach { this.softLinks.put(it, pid) }
      }
    }
  }

  fun applyExternalIndexChanges(diff: WorkspaceEntityStorageBuilderImpl) {
    externalIndices.keys.asSequence().filterNot { it in diff.indexes.externalIndices }.forEach {
      externalIndices.remove(it)
    }

    diff.indexes.externalIndices.keys.asSequence().filterNot { it in externalIndices.keys }.forEach {
      externalIndices[it] = ExternalEntityIndexImpl.MutableExternalEntityIndexImpl.from(diff.indexes.externalIndices[it]!!)
    }

    diff.indexes.externalIndices.forEach { (identifier, index) ->
      externalIndices[identifier]?.applyChanges(index)
    }
  }

  fun toImmutable(): StorageIndexes {
    val copiedLinks = this.softLinks.copy()
    val newVirtualFileIndex = virtualFileIndex.toImmutable()
    val newEntitySourceIndex = entitySourceIndex.toImmutable()
    val newPersistentIdIndex = persistentIdIndex.toImmutable()
    val newExternalIndices = ExternalEntityIndexImpl.MutableExternalEntityIndexImpl.toImmutable(externalIndices)
    return StorageIndexes(copiedLinks, newVirtualFileIndex, newEntitySourceIndex, newPersistentIdIndex, newExternalIndices)
  }
}