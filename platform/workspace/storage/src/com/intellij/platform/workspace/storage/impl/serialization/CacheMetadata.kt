// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.metadata.utils.collectTypesByFqn

internal fun loadCurrentEntitiesMetadata(cacheMetadata: CacheMetadata,
                                         typesResolver: EntityTypesResolver): List<StorageTypeMetadata>? {
  return cacheMetadata.map { (pluginId, typeMetadata) ->
    val currentTypeMetadata = TypeMetadataResolver.getInstance()
      .resolveTypeMetadataOrNull(typeMetadata.fqName, pluginId, typesResolver) ?: return null
    currentTypeMetadata
  }
}

internal fun getCacheMetadata(entityStorage: EntityStorageSnapshotImpl, typesResolver: EntityTypesResolver): CacheMetadata {
  val cacheMetadata = CacheMetadata.MutableCacheMetadata(typesResolver)

  //collecting entities with unique entity family
  entityStorage.entitiesByType.entityFamilies.forEachIndexed { i, entityFamily ->
    val entity = entityFamily?.entities?.firstNotNullOfOrNull { it }
    if (entity != null) {
      cacheMetadata.add(i.findWorkspaceEntity())
    }
  }

  //collecting unique entity source classes
  cacheMetadata.addAll(entityStorage.indexes.entitySourceIndex.entries().map { it::class.java })
  //collecting unique symbolic id classes
  cacheMetadata.addAll(entityStorage.indexes.symbolicIdIndex.entries().map { it::class.java })

  return cacheMetadata.toImmutable()
}


internal class CacheMetadata(
  private val metadataWithPluginId: List<Pair<PluginId, List<StorageTypeMetadata>>>
): Iterable<Pair<PluginId, StorageTypeMetadata>> {

  fun toList(): List<StorageTypeMetadata> = metadataWithPluginId.flatMap { it.second }

  override fun iterator(): Iterator<Pair<PluginId, StorageTypeMetadata>> {
    return CacheMetadataIterator(metadataWithPluginId.iterator())
  }


  internal class MutableCacheMetadata(private val typesResolver: EntityTypesResolver) {
    private val metadataByPluginId: MutableMap<PluginId, MutableMap<String, StorageTypeMetadata>> = hashMapOf()

    fun add(clazz: Class<*>) {
      val pluginId: PluginId = typesResolver.getPluginId(clazz)
      val typeMetadata = TypeMetadataResolver.getInstance().resolveTypeMetadata(clazz.name, pluginId, typesResolver)

      val metadataByFqn = metadataByPluginId.getOrPut(pluginId) { hashMapOf() }
      typeMetadata.collectTypesByFqn(metadataByFqn)
    }

    fun addAll(classes: Iterable<Class<*>>) = classes.forEach(::add)

    fun toImmutable(): CacheMetadata = CacheMetadata(metadataByPluginId.map { it.key to it.value.values.toList() })
  }


  private class CacheMetadataIterator(
    private val metadataWithPluginIdIterator: Iterator<Pair<PluginId, List<StorageTypeMetadata>>>
  ): Iterator<Pair<PluginId, StorageTypeMetadata>> {

    private var currentPluginId: PluginId = null
    private var metadataIterator: Iterator<StorageTypeMetadata>? = null

    override fun hasNext(): Boolean {
      updateIterator()
      return metadataIterator?.hasNext() == true
    }

    override fun next(): Pair<PluginId, StorageTypeMetadata> {
      updateIterator()
      val typeMetadata = metadataIterator?.next() ?: throw NoSuchElementException("Next on empty iterator")
      return Pair(currentPluginId, typeMetadata)
    }

    private fun updateIterator() {
      while (metadataWithPluginIdIterator.hasNext() && metadataIterator?.hasNext() != true) {
        val pair = metadataWithPluginIdIterator.next()
        currentPluginId = pair.first
        metadataIterator = pair.second.iterator()
      }
    }
  }
}


