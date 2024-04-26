// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.resolver

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.metadata.MetadataHash
import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.exceptions.MissingMetadataStorage
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import org.jetbrains.annotations.TestOnly

/**
 * Interface is needed for separate logic in tests
 *
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver]
 */
internal interface TypeMetadataResolver {
  fun resolveTypeMetadata(metadataStorage: MetadataStorage, typeFqn: String): StorageTypeMetadata

  fun resolveTypeMetadataOrNull(metadataStorage: MetadataStorage, typeFqn: String): StorageTypeMetadata?

  fun resolveTypeMetadataHash(metadataStorage: MetadataStorage, typeFqn: String): MetadataHash

  fun resolveTypeMetadataHashOrNull(metadataStorage: MetadataStorage, typeFqn: String): MetadataHash?

  fun resolveMetadataStorage(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataStorage

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
  override fun resolveTypeMetadata(metadataStorage: MetadataStorage, typeFqn: String): StorageTypeMetadata =
    metadataStorage.getMetadataByTypeFqn(typeFqn)

  override fun resolveTypeMetadataOrNull(metadataStorage: MetadataStorage, typeFqn: String): StorageTypeMetadata? =
    metadataStorage.getMetadataByTypeFqnOrNull(typeFqn)

  override fun resolveTypeMetadataHash(metadataStorage: MetadataStorage, typeFqn: String): MetadataHash =
    metadataStorage.getMetadataHashByTypeFqn(typeFqn)

  override fun resolveTypeMetadataHashOrNull(metadataStorage: MetadataStorage, typeFqn: String): MetadataHash? =
    metadataStorage.getMetadataHashByTypeFqnOrNull(typeFqn)

  override fun resolveMetadataStorage(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataStorage =
    MetadataStorageResolver.resolveMetadataStorage(typesResolver, typeFqn, pluginId)
}