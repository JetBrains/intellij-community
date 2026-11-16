// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.registration

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap
import com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.InternAwareReferenceResolver
import com.intellij.platform.workspace.storage.impl.serialization.ModuleId
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
import com.intellij.platform.workspace.storage.impl.toClassId
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import java.lang.reflect.Modifier

internal fun registerEntitiesClasses(kryo: Kryo, cacheMetadata: CacheMetadata,
                                     typesResolver: EntityTypesResolver, classCache: Object2IntWithDefaultMap<TypeInfo>) =
  EntitiesRegistrar(typesResolver, cacheMetadata.getMetadataWithPluginId(), classCache).registerClasses(kryo)


private class EntitiesRegistrar(
  private val typesResolver: EntityTypesResolver,
  private val typesMetadata: Iterable<Triple<PluginId, ModuleId, StorageTypeMetadata>>,
  private val classCache: Object2IntWithDefaultMap<TypeInfo>
): StorageRegistrar {

  override fun registerClasses(kryo: Kryo) {
    val valuePool = HashMap<Any, Any>()
    typesMetadata.forEach { (pluginId, moduleId, typeMetadata) ->
      // TODO("Test it. Custom classes can have another plugin id")
      val clazz = when (typeMetadata) {
        is EntityMetadata -> resolveClass(typeMetadata.entityDataFqName, pluginId, moduleId)
        is FinalClassMetadata -> resolveClass(typeMetadata.fqName, pluginId, moduleId)
        else -> null // we don't need to register abstract types
      }
      if (clazz != null) {
        if (typeMetadata is FinalClassMetadata.ObjectMetadata) {
          registerSingletonSerializer(kryo) { clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(0) }
        }
        else if (typeMetadata is FinalClassMetadata.ClassMetadata &&
                 kryo.classResolver.getRegistration(clazz) == null &&
                 isInternableValueClass(clazz)) {
          @Suppress("UNCHECKED_CAST")
          val defaultSerializer = kryo.getDefaultSerializer(clazz) as Serializer<Any>
          kryo.register(clazz, object : Serializer<Any>(defaultSerializer.acceptsNull, defaultSerializer.isImmutable) {
            override fun write(kryo: Kryo, output: Output, obj: Any?) {
              defaultSerializer.write(kryo, output, obj)
            }

            override fun read(kryo: Kryo, input: Input, type: Class<out Any>): Any? {
              val refResolver = if (kryo.references) kryo.referenceResolver as? InternAwareReferenceResolver else null
              val refId = refResolver?.lastAllocatedReadId ?: -1
              val value = defaultSerializer.read(kryo, input, type) ?: return null
              val canonical = valuePool.putIfAbsent(value, value) ?: value
              if (canonical !== value && refResolver != null) {
                refResolver.replaceReadObjectIfSame(refId, value, canonical)
              }
              return canonical
            }
          })
        }
        else {
          kryo.register(clazz)
        }
      }
    }
  }

  private fun isInternableValueClass(clazz: Class<*>): Boolean {
    if (clazz.getMethod("equals", Any::class.java).declaringClass == Any::class.java ||
        clazz.getMethod("hashCode").declaringClass == Any::class.java) {
      return false
    }
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
      for (field in current.declaredFields) {
        if (!Modifier.isStatic(field.modifiers) && (!Modifier.isFinal(field.modifiers) || field.type.isArray)) {
          return false
        }
      }
      current = current.superclass
    }
    return true
  }

  private fun resolveClass(fqName: String, pluginId: PluginId, moduleId: ModuleId): Class<*> {
    val resolvedClass = typesResolver.resolveClass(fqName, pluginId, moduleId)
    classCache.putIfAbsent(TypeInfo(fqName, pluginId, moduleId), resolvedClass.toClassId())
    return resolvedClass
  }
}