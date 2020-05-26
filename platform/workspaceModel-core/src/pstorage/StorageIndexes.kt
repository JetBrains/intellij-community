// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex
import com.intellij.workspace.api.pstorage.indices.EntityStorageInternalIndex
import com.intellij.workspace.api.pstorage.indices.VirtualFileIndex

internal open class StorageIndexes(
  internal open val softLinks: Multimap<PersistentEntityId<*>, PId>,
  internal open val virtualFileIndex: VirtualFileIndex,
  internal open val entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
  internal open val persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>,
  internal open val externalIndices: Map<String, ExternalEntityIndex<*>>
) {
  companion object {
    val EMPTY = StorageIndexes(HashMultimap.create(), VirtualFileIndex(), EntityStorageInternalIndex(), EntityStorageInternalIndex(),
                               HashMap())
  }

  fun toMutable(): MutableStorageIndexes {
    val copiedSoftLinks = HashMultimap.create(softLinks)
    val copiedVirtualFileIndex = VirtualFileIndex.MutableVirtualFileIndex.from(virtualFileIndex)
    val copiedEntitySourceIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(entitySourceIndex)
    val copiedPersistentIdIndex = EntityStorageInternalIndex.MutableEntityStorageInternalIndex.from(persistentIdIndex)
    val copiedExternalIndices = ExternalEntityIndex.MutableExternalEntityIndex.fromMap(externalIndices)
    return MutableStorageIndexes(copiedSoftLinks, copiedVirtualFileIndex, copiedEntitySourceIndex, copiedPersistentIdIndex, copiedExternalIndices)
  }
}

internal class MutableStorageIndexes(
  override val softLinks: Multimap<PersistentEntityId<*>, PId>,
  override val virtualFileIndex: VirtualFileIndex.MutableVirtualFileIndex,
  override val entitySourceIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<EntitySource>,
  override val persistentIdIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<PersistentEntityId<*>>,
  override val externalIndices: MutableMap<String, ExternalEntityIndex.MutableExternalEntityIndex<*>>
) : StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, externalIndices) {

  fun <T : TypedEntity> entityAdded(pEntityData: PEntityData<T>, builder: PEntityStorageBuilder) {
    val pid = pEntityData.createPid()

    // Update soft links index
    if (pEntityData is PSoftLinkable) {
      for (link in pEntityData.getLinks()) {
        softLinks.put(link, pid)
      }
    }

    entitySourceIndex.index(pid, pEntityData.entitySource)

    pEntityData.persistentId(builder)?.let { persistentId ->
      if (persistentIdIndex.getIdsByEntry(persistentId) != null) error("Entity with persistentId: $persistentId already exist")
      persistentIdIndex.index(pid, persistentId)
    }
  }

  fun updateSoftLinksIndex(softLinkable: PSoftLinkable) {
    val pid = (softLinkable as PEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.put(link, pid)
    }
  }

  fun removeFromSoftLinksIndex(softLinkable: PSoftLinkable) {
    val pid = (softLinkable as PEntityData<*>).createPid()

    for (link in softLinkable.getLinks()) {
      softLinks.remove(link, pid)
    }
  }

  fun updateIndices(oldEntityId: PId, newEntityId: PId, builder: AbstractPEntityStorage) {
    builder.indexes.virtualFileIndex.getVirtualFiles(oldEntityId)?.forEach { virtualFileIndex.index(newEntityId, listOf(it)) }
    builder.indexes.entitySourceIndex.getEntryById(oldEntityId)?.also { entitySourceIndex.index(newEntityId, it) }
    builder.indexes.persistentIdIndex.getEntryById(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  fun removeFromIndices(entityId: PId) {
    virtualFileIndex.index(entityId)
    entitySourceIndex.index(entityId)
    persistentIdIndex.index(entityId)
  }

  @OptIn(ExperimentalStdlibApi::class)
  fun <T : TypedEntity> updateSoftReferences(beforePersistentId: PersistentEntityId<*>?,
                                             beforeSoftLinks: List<PersistentEntityId<*>>?,
                                             copiedData: PEntityData<T>,
                                             builder: PEntityStorageBuilder) {
    val pid = copiedData.createPid()
    if (beforePersistentId != null) {
      val afterPersistentId = copiedData.persistentId(builder) ?: error("Persistent id expected")
      if (beforePersistentId != afterPersistentId) {
        val updatedIds = mutableListOf(beforePersistentId to afterPersistentId)
        while (updatedIds.isNotEmpty()) {
          val (beforeId, afterId) = updatedIds.removeFirst()
          val nonNullSoftLinks = softLinks[beforeId] ?: continue
          for (id: PId in nonNullSoftLinks) {
            val pEntityData = builder.entitiesByType.getEntityDataForModification(id) as PEntityData<TypedEntity>
            val updated = (pEntityData as PSoftLinkable).updateLink(beforeId, afterId, updatedIds)

            if (updated) {
              val softLinkedPid = pEntityData.createPid()
              val softLinkedParents = builder.refs.getParentRefsOfChild(softLinkedPid)
              val softLinkedChildren = builder.refs.getChildrenRefsOfParentBy(softLinkedPid)
              builder.updateChangeLog {
                it.add(PEntityStorageBuilder.ChangeEntry.ReplaceEntity(pEntityData, softLinkedChildren, softLinkedParents))
              }
            }
          }
          softLinks.putAll(afterId, softLinks[beforeId])
          softLinks.removeAll(beforeId)
        }
      }
    }

    if (beforeSoftLinks != null) {
      val afterSoftLinks = (copiedData as PSoftLinkable).getLinks()
      if (beforeSoftLinks != afterSoftLinks) {
        beforeSoftLinks.forEach { this.softLinks.remove(it, pid) }
        afterSoftLinks.forEach { this.softLinks.put(it, pid) }
      }
    }
  }

  fun applyExternalIndexChanges(diff: PEntityStorageBuilder) {
    val removed = externalIndices.keys.toMutableSet()
    removed.removeAll(diff.indexes.externalIndices.keys)
    removed.forEach { externalIndices.remove(it) }

    val added = diff.indexes.externalIndices.keys.toMutableSet()
    added.removeAll(externalIndices.keys)
    added.forEach { externalIndices[it] = ExternalEntityIndex.MutableExternalEntityIndex.from(diff.indexes.externalIndices[it]!!) }

    diff.indexes.externalIndices.forEach { (identifier, index) ->
      externalIndices[identifier]?.applyChanges(index)
    }
  }

  fun toImmutable(): StorageIndexes {
    val copiedLinks = HashMultimap.create(this.softLinks)
    val newVirtualFileIndex = virtualFileIndex.toImmutable()
    val newEntitySourceIndex = entitySourceIndex.toImmutable()
    val newPersistentIdIndex = persistentIdIndex.toImmutable()
    val newExternalIndices = ExternalEntityIndex.MutableExternalEntityIndex.toImmutable(externalIndices)
    return StorageIndexes(copiedLinks, newVirtualFileIndex, newEntitySourceIndex, newPersistentIdIndex, newExternalIndices)
  }
}