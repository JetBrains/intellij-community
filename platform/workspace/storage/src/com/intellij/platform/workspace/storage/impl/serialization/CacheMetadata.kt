// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.EntityStorageSnapshotImpl
import com.intellij.platform.workspace.storage.impl.findWorkspaceEntity
import com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata.SerializableTypeMetadata
import com.intellij.platform.workspace.storage.metadata.MetadataHash
import com.intellij.platform.workspace.storage.metadata.diff.ComparisonResult
import com.intellij.platform.workspace.storage.metadata.diff.Equal
import com.intellij.platform.workspace.storage.metadata.diff.NotEqual
import com.intellij.platform.workspace.storage.metadata.diff.TypesMetadataComparator
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.metadata.utils.collectTypesByFqn
import java.util.LinkedHashMap

internal fun compareWithCurrentEntitiesMetadata(cacheMetadata: CacheMetadata,
                                                typesResolver: EntityTypesResolver): ComparisonResult {
  val metadataResolver = TypeMetadataResolver.getInstance()

  cacheMetadata.forEach { (pluginId, cacheTypeMetadata) ->
      val typeFqn = cacheTypeMetadata.metadata.fqName
      val currentTypeMetadataHash = metadataResolver.resolveTypeMetadataHashOrNull(typesResolver, typeFqn, pluginId)
                                    ?: return NotEqual("Failed to load existing metadata for type $typeFqn")

      if (cacheTypeMetadata.metadataHash != currentTypeMetadataHash) {
        val currentTypeMetadata = metadataResolver.resolveTypeMetadata(typesResolver, typeFqn, pluginId)
        val comparisonResult = TypesMetadataComparator(cacheTypeMetadata.metadata, currentTypeMetadata)
          .areEquals(cacheTypeMetadata.metadata, currentTypeMetadata)
        if (comparisonResult.areEquals) {
          throw IllegalStateException(
            "Hashes of the type $typeFqn differ in the cache (${cacheTypeMetadata.metadataHash}) from the current one ($currentTypeMetadataHash)"
            + ", although after comparison they are equal."
          )
        }
        return comparisonResult
      }
  }
  return Equal
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

  return cacheMetadata.toImmutable(typesResolver)
}


internal class CacheMetadata(
  private val metadataByPluginId: LinkedHashMap<PluginId, List<SerializableTypeMetadata>>
): Iterable<Pair<PluginId, SerializableTypeMetadata>> {

  internal class SerializableTypeMetadata(val metadata: StorageTypeMetadata, val metadataHash: MetadataHash)

  fun getMetadataWithPluginId(): Iterable<Pair<PluginId, StorageTypeMetadata>> = this.map { it.first to it.second.metadata }

  override fun iterator(): Iterator<Pair<PluginId, SerializableTypeMetadata>> {
    return CacheMetadataIterator(metadataByPluginId.iterator())
  }


  internal class MutableCacheMetadata(private val typesResolver: EntityTypesResolver) {
    private val metadataByPluginId: MutableMap<PluginId, MutableMap<String, StorageTypeMetadata>> = hashMapOf()

    fun add(clazz: Class<*>) {
      val pluginId: PluginId = typesResolver.getPluginId(clazz)
      val typeMetadata = TypeMetadataResolver.getInstance().resolveTypeMetadata(typesResolver, clazz.name, pluginId)

      val metadataByFqn = metadataByPluginId.getOrPut(pluginId) { hashMapOf() }
      typeMetadata.collectTypesByFqn(metadataByFqn)
    }

    fun addAll(classes: Iterable<Class<*>>) = classes.forEach(::add)

    fun toImmutable(typesResolver: EntityTypesResolver): CacheMetadata {
      val map = LinkedHashMap<PluginId, List<SerializableTypeMetadata>>()
      metadataByPluginId.forEach { (pluginId, metadataByFqn) ->
        val serializableTypesMetadata = metadataByFqn.values.map {
          val typeMetadataHash = TypeMetadataResolver.getInstance().resolveTypeMetadataHash(typesResolver, it.fqName, pluginId)
          SerializableTypeMetadata(it, typeMetadataHash)
        }
        map[pluginId] = serializableTypesMetadata
      }
      return CacheMetadata(map)
    }
  }


  private class CacheMetadataIterator(
    private val metadataWithPluginIdIterator: Iterator<Map.Entry<PluginId, List<SerializableTypeMetadata>>>
  ): Iterator<Pair<PluginId, SerializableTypeMetadata>> {

    private var currentPluginId: PluginId = null
    private var metadataIterator: Iterator<SerializableTypeMetadata>? = null

    override fun hasNext(): Boolean {
      updateIterator()
      return metadataIterator?.hasNext() == true
    }

    override fun next(): Pair<PluginId, SerializableTypeMetadata> {
      updateIterator()
      val typeMetadata = metadataIterator?.next() ?: throw NoSuchElementException("Next on empty iterator")
      return Pair(currentPluginId, typeMetadata)
    }

    private fun updateIterator() {
      while (metadataWithPluginIdIterator.hasNext() && metadataIterator?.hasNext() != true) {
        val pair = metadataWithPluginIdIterator.next()
        currentPluginId = pair.key
        metadataIterator = pair.value.iterator()
      }
    }
  }
}


