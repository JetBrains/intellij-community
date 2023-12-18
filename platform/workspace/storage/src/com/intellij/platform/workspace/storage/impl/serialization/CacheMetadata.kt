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

internal fun compareWithCurrentEntitiesMetadata(cacheMetadata: CacheMetadata,
                                                typesResolver: EntityTypesResolver): ComparisonResult {
  val metadataResolver = TypeMetadataResolver.getInstance()

  cacheMetadata.forEach { (id, cacheTypeMetadata) ->
    val typeFqn = cacheTypeMetadata.metadata.fqName
    val metadataStorage = metadataResolver.resolveMetadataStorage(typesResolver, id.metadataStorageFqn, id.pluginId)
    val currentTypeMetadataHash = metadataResolver.resolveTypeMetadataHashOrNull(metadataStorage, typeFqn)
                                  ?: return NotEqual("Failed to load existing metadata for type $typeFqn")

    if (cacheTypeMetadata.metadataHash != currentTypeMetadataHash) {
      val currentTypeMetadata = metadataResolver.resolveTypeMetadata(metadataStorage, typeFqn)
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
  private val metadataById: LinkedHashMap<Id, List<SerializableTypeMetadata>>
): Iterable<Pair<CacheMetadata.Id, SerializableTypeMetadata>> {

  internal data class Id(val pluginId: PluginId, val metadataStorageFqn: String)

  internal class SerializableTypeMetadata(val metadata: StorageTypeMetadata, val metadataHash: MetadataHash)

  fun getMetadataWithPluginId(): Iterable<Pair<PluginId, StorageTypeMetadata>> = this.map { it.first.pluginId to it.second.metadata }

  override fun iterator(): Iterator<Pair<Id, SerializableTypeMetadata>> {
    return CacheMetadataIterator(metadataById.iterator())
  }


  internal class MutableCacheMetadata(private val typesResolver: EntityTypesResolver) {
    private val metadataById: MutableMap<Id, MutableMap<String, StorageTypeMetadata>> = hashMapOf()

    fun add(clazz: Class<*>) {
      val pluginId: PluginId = typesResolver.getPluginId(clazz)
      val metadataStorage = TypeMetadataResolver.getInstance().resolveMetadataStorage(typesResolver, clazz.name, pluginId)
      val typeMetadata = TypeMetadataResolver.getInstance().resolveTypeMetadata(metadataStorage, clazz.name)

      val metadataByFqn = metadataById.getOrPut(Id(pluginId, metadataStorage::class.java.name)) { hashMapOf() }
      typeMetadata.collectTypesByFqn(metadataByFqn, metadataStorage)
    }

    fun addAll(classes: Iterable<Class<*>>) = classes.forEach(::add)

    fun toImmutable(typesResolver: EntityTypesResolver): CacheMetadata {
      val map = LinkedHashMap<Id, List<SerializableTypeMetadata>>()
      metadataById.forEach { (id, metadataByFqn) ->
        val metadataStorage = TypeMetadataResolver.getInstance().resolveMetadataStorage(typesResolver, id.metadataStorageFqn, id.pluginId)
        val serializableTypesMetadata = metadataByFqn.values.map {
          val typeMetadataHash = TypeMetadataResolver.getInstance().resolveTypeMetadataHash(metadataStorage, it.fqName)
          SerializableTypeMetadata(it, typeMetadataHash)
        }
        map[id] = serializableTypesMetadata
      }
      return CacheMetadata(map)
    }
  }


  private class CacheMetadataIterator(
    private val metadataWithIdIterator: Iterator<Map.Entry<Id, List<SerializableTypeMetadata>>>
  ): Iterator<Pair<Id, SerializableTypeMetadata>> {

    private var currentId: Id? = null
    private var metadataIterator: Iterator<SerializableTypeMetadata>? = null

    override fun hasNext(): Boolean {
      updateIterator()
      return metadataIterator?.hasNext() == true
    }

    override fun next(): Pair<Id, SerializableTypeMetadata> {
      updateIterator()
      val typeMetadata = metadataIterator?.next() ?: throw NoSuchElementException("Next on empty iterator")
      return Pair(currentId!!, typeMetadata)
    }

    private fun updateIterator() {
      while (metadataWithIdIterator.hasNext() && metadataIterator?.hasNext() != true) {
        val pair = metadataWithIdIterator.next()
        currentId = pair.key
        metadataIterator = pair.value.iterator()
      }
    }
  }
}


