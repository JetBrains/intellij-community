// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.registration

import com.esotericsoftware.kryo.kryo5.Kryo
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap
import com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
import com.intellij.platform.workspace.storage.impl.toClassId
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata

internal fun registerEntitiesClasses(kryo: Kryo, cacheMetadata: CacheMetadata,
                                     typesResolver: EntityTypesResolver, classCache: Object2IntWithDefaultMap<TypeInfo>) =
  EntitiesRegistrar(typesResolver, cacheMetadata.getMetadataWithPluginId(), classCache).registerClasses(kryo)


private class EntitiesRegistrar(
  private val typesResolver: EntityTypesResolver,
  private val typesMetadata: Iterable<Pair<PluginId, StorageTypeMetadata>>,
  private val classCache: Object2IntWithDefaultMap<TypeInfo>
): StorageRegistrar {

  override fun registerClasses(kryo: Kryo) {
    typesMetadata.forEach { (pluginId, typeMetadata) ->
      // TODO("Test it. Custom classes can have another plugin id")
      val clazz = when (typeMetadata) {
        is EntityMetadata -> resolveClass(typeMetadata.entityDataFqName, pluginId)
        is FinalClassMetadata -> resolveClass(typeMetadata.fqName, pluginId)
        else -> null // we don't need to register abstract types
      }
      if (clazz != null) {
        if (typeMetadata is FinalClassMetadata.ObjectMetadata) {
          registerSingletonSerializer(kryo) { clazz.getDeclaredField("INSTANCE").get(0) }
        }
        else {
          kryo.register(clazz)
        }
      }
    }
  }

  private fun resolveClass(fqName: String, pluginId: PluginId): Class<*> {
    val resolvedClass = typesResolver.resolveClass(fqName, pluginId)
    classCache.putIfAbsent(TypeInfo(fqName, pluginId), resolvedClass.toClassId())
    return resolvedClass
  }
}