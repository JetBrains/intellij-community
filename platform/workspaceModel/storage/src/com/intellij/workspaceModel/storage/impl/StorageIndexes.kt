// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntityData
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex
import com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex.MutableVirtualFileIndex.Companion.VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY

internal open class StorageIndexes(
  // List of IDs of entities that use this particular persistent id
  internal open val softLinks: MultimapStorageIndex,
  internal open val virtualFileIndex: VirtualFileIndex,
  internal open val entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
  internal open val persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>,
  internal open val externalMappings: Map<String, ExternalEntityMappingImpl<*>>
) {

  constructor(softLinks: MultimapStorageIndex,
              virtualFileIndex: VirtualFileIndex,
              entitySourceIndex: EntityStorageInternalIndex<EntitySource>,
              persistentIdIndex: EntityStorageInternalIndex<PersistentEntityId<*>>
  ) : this(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, emptyMap())

  companion object {
    val EMPTY = StorageIndexes(MultimapStorageIndex(), VirtualFileIndex(), EntityStorageInternalIndex(false), EntityStorageInternalIndex(true),
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
    assertEntitySourceIndex(storage)

    assertPersistentIdIndex(storage)

    assertSoftLinksIndex(storage)

    virtualFileIndex.assertConsistency()

    // Assert external mappings
    for ((_, mappings) in externalMappings) {
      for ((id, obj) in mappings.index) {
        assert(storage.entityDataById(id) != null) { "Missing entity by id: $id" }
      }
    }
  }

  private fun assertSoftLinksIndex(storage: AbstractEntityStorage) {

    // XXX skipped size check

    storage.entitiesByType.entityFamilies.forEachIndexed { i, family ->
      if (family == null) return@forEachIndexed
      if (family.entities.firstOrNull { it != null } !is SoftLinkable) return@forEachIndexed
      var mutableId = EntityId(0, i)
      family.entities.forEach { data ->
        if (data == null) return@forEach
        mutableId = mutableId.copy(arrayId = data.id)
        val expectedLinks = softLinks.getEntriesById(mutableId)
        if (data is ModuleEntityData) {
          assertModuleSoftLinks(data, expectedLinks)
        }
        else {
          val actualLinks = (data as SoftLinkable).getLinks()
          assert(expectedLinks.size == actualLinks.size) { "Different sizes: $expectedLinks, $actualLinks" }
          assert(expectedLinks.all { it in actualLinks }) { "Different sets: $expectedLinks, $actualLinks" }
        }
      }
    }
  }

  // XXX: Hack to speed up module links assertion
  private fun assertModuleSoftLinks(entityData: ModuleEntityData, expectedLinks: Set<PersistentEntityId<*>>) {
    val actualRefs = HashSet<Any>(entityData.dependencies.size)
    entityData.dependencies.forEach { dependency ->
      when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> {
          assert(dependency.module in expectedLinks)
          actualRefs += dependency.module
        }
        is ModuleDependencyItem.Exportable.LibraryDependency -> {
          assert(dependency.library in expectedLinks)
          actualRefs += dependency.library
        }
        else -> Unit
      }
    }
    assert(actualRefs.size == expectedLinks.size)
  }

  private fun assertPersistentIdIndex(storage: AbstractEntityStorage) {

    var expectedSize = 0
    storage.entitiesByType.entityFamilies.forEachIndexed { i, family ->
      if (family == null) return@forEachIndexed
      if (family.entities.firstOrNull { it != null }?.persistentId(storage) == null) return@forEachIndexed
      var mutableId = EntityId(0, i)
      family.entities.forEach { data ->
        if (data == null) return@forEach
        mutableId = mutableId.copy(arrayId = data.id)
        val expectedPersistentId = persistentIdIndex.getEntryById(mutableId)
        assert(expectedPersistentId == data.persistentId(storage)) { "Entity $data isn't found in persistent id index. PersistentId: ${data.persistentId(storage)}, Id: $mutableId. Expected entity source: $expectedPersistentId" }
        expectedSize++
      }
    }

    assert(expectedSize == persistentIdIndex.index.size) { "Incorrect size of persistent id index. Expected: $expectedSize, actual: ${persistentIdIndex.index.size}" }
  }

  private fun assertEntitySourceIndex(storage: AbstractEntityStorage) {

    var expectedSize = 0
    storage.entitiesByType.entityFamilies.forEachIndexed { i, family ->
      if (family == null) return@forEachIndexed
      // Optimization to skip useless conversion of classes
      var mutableId = EntityId(0, i)
      family.entities.forEach { data ->
        if (data == null) return@forEach
        mutableId = mutableId.copy(arrayId = data.id)
        val expectedEntitySource = entitySourceIndex.getEntryById(mutableId)
        assert(expectedEntitySource == data.entitySource) { "Entity $data isn't found in entity source index. Entity source: ${data.entitySource}, Id: $mutableId. Expected entity source: $expectedEntitySource" }
        expectedSize++
      }
    }

    assert(expectedSize == entitySourceIndex.index.size) { "Incorrect size of entity source index. Expected: $expectedSize, actual: ${entitySourceIndex.index.size}" }
  }
}

internal class MutableStorageIndexes(
  override val softLinks: MultimapStorageIndex.MutableMultimapStorageIndex,
  override val virtualFileIndex: VirtualFileIndex.MutableVirtualFileIndex,
  override val entitySourceIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<EntitySource>,
  override val persistentIdIndex: EntityStorageInternalIndex.MutableEntityStorageInternalIndex<PersistentEntityId<*>>,
  override val externalMappings: MutableMap<String, MutableExternalEntityMappingImpl<*>>
) : StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex, externalMappings) {

  fun <T : WorkspaceEntity> entityAdded(entityData: WorkspaceEntityData<T>, builder: WorkspaceEntityStorageBuilderImpl) {
    val pid = entityData.createEntityId()

    // Update soft links index
    if (entityData is SoftLinkable) {
      for (link in entityData.getLinks()) {
        softLinks.index(pid, link)
      }
    }

    val entitySource = entityData.entitySource
    entitySourceIndex.index(pid, entitySource)

    entityData.persistentId(builder)?.let { persistentId ->
      persistentIdIndex.index(pid, persistentId)
    }

    entitySource.virtualFileUrl?.let { virtualFileIndex.index(pid, VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY, it) }
  }

  fun updateSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createEntityId()

    for (link in softLinkable.getLinks()) {
      softLinks.index(pid, link)
    }
  }

  fun removeFromSoftLinksIndex(softLinkable: SoftLinkable) {
    val pid = (softLinkable as WorkspaceEntityData<*>).createEntityId()

    for (link in softLinkable.getLinks()) {
      softLinks.remove(pid, link)
    }
  }

  fun updateIndices(oldEntityId: EntityId, newEntityData: WorkspaceEntityData<*>, builder: AbstractEntityStorage) {
    val newEntityId = newEntityData.createEntityId()
    virtualFileIndex.updateIndex(oldEntityId, newEntityId, builder.indexes.virtualFileIndex)
    entitySourceIndex.index(newEntityId, newEntityData.entitySource)
    builder.indexes.persistentIdIndex.getEntryById(oldEntityId)?.also { persistentIdIndex.index(newEntityId, it) }
  }

  fun removeFromIndices(entityId: EntityId) {
    entitySourceIndex.index(entityId)
    persistentIdIndex.index(entityId)
    virtualFileIndex.removeRecordsByEntityId(entityId)
    externalMappings.values.forEach { it.remove(entityId) }
  }

  fun <T : WorkspaceEntity> simpleUpdateSoftReferences(copiedData: WorkspaceEntityData<T>) {
    val pid = copiedData.createEntityId()
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
                                  replaceMap: HashBiMap<EntityId, EntityId>,
                                  target: WorkspaceEntityStorageBuilderImpl) {
    diff.indexes.externalMappings.keys.asSequence().filterNot { it in externalMappings.keys }.forEach {
      externalMappings[it] = MutableExternalEntityMappingImpl<Any>()
    }

    diff.indexes.externalMappings.forEach { (identifier, index) ->
      val mapping = externalMappings[identifier]
      if (mapping != null) {
        mapping.applyChanges(index, replaceMap, target)
        if (mapping.index.isEmpty()) {
          externalMappings.remove(identifier)
        }
      }
    }
  }

  fun updateExternalMappingForEntityId(oldId: EntityId, newId: EntityId = oldId, originStorageIndexes: StorageIndexes) {
    originStorageIndexes.externalMappings.forEach { (id, mapping) ->
      val data = mapping.index[oldId] ?: return@forEach
      val externalMapping = externalMappings[id]
      if (externalMapping == null) {
        val newMapping = MutableExternalEntityMappingImpl<Any>()
        newMapping.add(newId, data)
        externalMappings[id] = newMapping
      } else {
        externalMapping as MutableExternalEntityMappingImpl<Any>
        externalMapping.add(newId, data)
      }
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
