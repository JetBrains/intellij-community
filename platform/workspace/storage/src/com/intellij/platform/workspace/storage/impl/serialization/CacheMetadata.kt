// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver

internal fun loadCurrentEntitiesMetadata(cacheMetadata: CacheMetadata,
                                         typesResolver: EntityTypesResolver): List<StorageTypeMetadata>? {
  val currentMetadata = arrayListOf<StorageTypeMetadata>()

  cacheMetadata.toListWithPluginId().forEach { (pluginId, typeMetadata) ->
    val currentTypeMetadata = TypeMetadataResolver.getInstance()
      .resolveTypeMetadataOrNull(typeMetadata.fqName, pluginId, typesResolver) ?: return null
    currentMetadata.add(currentTypeMetadata)
  }

  return currentMetadata
}

internal fun getCacheMetadata(entityStorage: EntityStorageSnapshotImpl, typesResolver: EntityTypesResolver): CacheMetadata {
  val cacheMetadata = CacheMetadata()

  //collecting entities with unique entity family
  entityStorage.entitiesByType.entityFamilies.forEachIndexed { i, entityFamily ->
    val entity = entityFamily?.entities?.firstNotNullOfOrNull { it }
    if (entity != null) {
      val pluginId = typesResolver.getPluginId(i.findWorkspaceEntity())
      cacheMetadata.add(pluginId, entity.getMetadata())
    }
  }

  collectIndexes(
    cacheMetadata,
    entityStorage.indexes.entitySourceIndex.entries(),
    entityStorage.indexes.symbolicIdIndex.entries(),
    typesResolver
  )

  return cacheMetadata
}

internal fun getCacheMetadata(
  entityDataSequence: Sequence<WorkspaceEntityData<*>>,
  entitySources: Collection<EntitySource>,
  symbolicIds: Collection<SymbolicEntityId<*>>,
  typesResolver: EntityTypesResolver
): CacheMetadata {
  val cacheMetadata = CacheMetadata()

  //collecting entities with unique entity family
  val processedEntityFamilies = hashSetOf<Class<*>>()
  val uniqueEntityDataSequence = entityDataSequence.filter {
    val notProcessed = !processedEntityFamilies.contains(it.getEntityInterface())
    if (notProcessed) {
      processedEntityFamilies.add(it.getEntityInterface())
    }
    notProcessed
  }

  uniqueEntityDataSequence.forEach { entityData ->
    val pluginId = typesResolver.getPluginId(entityData.getEntityInterface())
    cacheMetadata.add(pluginId, entityData.getMetadata())
  }

  collectIndexes(cacheMetadata, entitySources, symbolicIds, typesResolver)

  return cacheMetadata
}

private fun collectIndexes(
  cacheMetadata: CacheMetadata,
  entitySources: Collection<EntitySource>,
  symbolicIds: Collection<SymbolicEntityId<*>>,
  typesResolver: EntityTypesResolver
) {
  val classes: MutableSet<Class<*>> = LinkedHashSet()
  //collecting unique entity source classes
  classes.addAll(entitySources.map { it::class.java })
  //collecting unique symbolic id classes
  classes.addAll(symbolicIds.map { it::class.java })

  classes.forEach {
    val pluginId: PluginId = typesResolver.getPluginId(it)
    val typeMetadata = TypeMetadataResolver.getInstance().resolveTypeMetadata(it.name, pluginId, typesResolver)
    cacheMetadata.add(pluginId, typeMetadata)
  }
}


internal class CacheMetadata(
  private val metadataByPluginId: MutableMap<PluginId, MutableList<StorageTypeMetadata>> = hashMapOf()
) {
  fun add(pluginId: PluginId, metadata: StorageTypeMetadata) {
    metadataByPluginId.getOrPut(pluginId) { arrayListOf() }.add(metadata)
  }

  fun toList(): List<StorageTypeMetadata> = metadataByPluginId.values.flatten()

  fun toListWithPluginId(): List<Pair<PluginId, StorageTypeMetadata>> {
    val metadataWithPluginId = arrayListOf<Pair<PluginId, StorageTypeMetadata>>()
    metadataByPluginId.forEach { (pluginId, typesMetadata) ->
      typesMetadata.forEach { metadataWithPluginId.add(pluginId to it) }
    }
    return metadataWithPluginId
  }
}


