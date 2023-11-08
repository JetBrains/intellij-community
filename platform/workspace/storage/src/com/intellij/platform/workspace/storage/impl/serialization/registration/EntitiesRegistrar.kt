// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.registration

import com.esotericsoftware.kryo.kryo5.Kryo
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
import com.intellij.platform.workspace.storage.impl.toClassId
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.utils.collectClasses
import it.unimi.dsi.fastutil.objects.Object2IntMap

internal fun registerEntitiesClasses(kryo: Kryo, cacheMetadata: CacheMetadata,
                                     typesResolver: EntityTypesResolver, classCache: Object2IntMap<TypeInfo>) =
  EntitiesRegistrar(typesResolver, cacheMetadata.toListWithPluginId(), classCache).registerClasses(kryo)


private class EntitiesRegistrar(
  private val typesResolver: EntityTypesResolver,
  private val entitiesMetadata: List<Pair<PluginId, StorageTypeMetadata>>,
  private val classCache: Object2IntMap<TypeInfo>
): StorageRegistrar {

  override fun registerClasses(kryo: Kryo) {
    val collector = UsedClassesCollector()

    entitiesMetadata.forEach { (pluginId, typeMetadata) ->
      val (objects, classes) = typeMetadata.collectClasses().partition { it is FinalClassMetadata.ObjectMetadata }

      // TODO("Test it. Custom classes can have another plugin id")
      classes.forEach { collector.add(resolveClass(it.fqName, pluginId)) }
      objects.forEach { collector.addObject(resolveClass(it.fqName, pluginId)) }

      if (typeMetadata is EntityMetadata) {
        collector.add(resolveClass(typeMetadata.entityDataFqName, pluginId))
      }
    }

    collector.collectionObjects.sortedBy { it.name }.forEach {
      registerSingletonSerializer(kryo) { it.getDeclaredField("INSTANCE").get(0) }
    }

    collector.collection.sortedBy { it.name }.forEach { kryo.register(it) }
  }

  private fun resolveClass(fqName: String, pluginId: PluginId): Class<*> {
    val resolvedClass = typesResolver.resolveClass(fqName, pluginId)
    classCache.putIfAbsent(TypeInfo(fqName, pluginId), resolvedClass.toClassId())
    return resolvedClass
  }
}

private class UsedClassesCollector(
  val collection: MutableSet<Class<out Any>> = HashSet(),
  val collectionObjects: MutableSet<Class<out Any>> = HashSet(),
) {
  fun add(clazz: Class<out Any>) {
    collection.add(clazz)
  }

  fun addObject(clazz: Class<out Any>) {
    collectionObjects.add(clazz)
  }
}