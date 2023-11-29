// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.resolver

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.metadata.MetadataHash
import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import org.jetbrains.annotations.TestOnly

/**
 * Interface is needed for separate logic in tests
 *
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver]
 */
internal interface TypeMetadataResolver {
  fun resolveTypeMetadata(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): StorageTypeMetadata

  fun resolveTypeMetadataHashOrNull(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash?

  fun resolveTypeMetadataHash(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash


  companion object {
    internal fun getInstance(): TypeMetadataResolver = INSTANCE

    @TestOnly
    internal fun replaceTypeMetadataResolver(typeMetadataResolver: TypeMetadataResolver) {
      INSTANCE = typeMetadataResolver
    }

    private var INSTANCE: TypeMetadataResolver = TypeMetadataResolverImpl
  }
}


internal object TypeMetadataResolverImpl: TypeMetadataResolver {

  override fun resolveTypeMetadata(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): StorageTypeMetadata {
    val metadataStorage = resolveMetadataStorage(typesResolver, typeFqn, pluginId)
    return metadataStorage.getMetadataByTypeFqn(typeFqn)
  }

  override fun resolveTypeMetadataHashOrNull(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash? {
    val metadataStorage = resolveMetadataStorageOrNull(typesResolver, typeFqn, pluginId)
    return metadataStorage?.getMetadataHashByTypeFqnOrNull(typeFqn)
  }

  override fun resolveTypeMetadataHash(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash {
    val metadataStorage = resolveMetadataStorage(typesResolver, typeFqn, pluginId)
    return metadataStorage.getMetadataHashByTypeFqn(typeFqn)
  }


  private fun resolveMetadataStorageOrNull(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataStorage? =
    MetadataStorageResolver.resolveMetadataStorageOrNull(typesResolver, extractPackageName(typeFqn), pluginId)

  private fun resolveMetadataStorage(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataStorage =
    MetadataStorageResolver.resolveMetadataStorage(typesResolver, extractPackageName(typeFqn), pluginId)


  private fun extractPackageName(typeFqn: String): String = typeFqn.substringBeforeLast('.', "")
}