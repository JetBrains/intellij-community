// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.resolver

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import org.jetbrains.annotations.TestOnly

/**
 * Interface is needed for separate logic in tests
 *
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver]
*/
internal interface TypeMetadataResolver {
  fun resolveTypeMetadataOrNull(typeFqn: String, pluginId: PluginId, typesResolver: EntityTypesResolver): StorageTypeMetadata?

  fun resolveTypeMetadata(typeFqn: String, pluginId: PluginId, typesResolver: EntityTypesResolver): StorageTypeMetadata

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
  override fun resolveTypeMetadataOrNull(typeFqn: String, pluginId: PluginId, typesResolver: EntityTypesResolver): StorageTypeMetadata? {
    val metadataStorage = MetadataStorageResolver.resolveMetadataStorageOrNull(typesResolver, extractPackageName(typeFqn), pluginId)
    return metadataStorage?.getMetadataByTypeFqnOrNull(typeFqn)
  }

  override fun resolveTypeMetadata(typeFqn: String, pluginId: PluginId, typesResolver: EntityTypesResolver): StorageTypeMetadata {
    val metadataStorage = MetadataStorageResolver.resolveMetadataStorage(typesResolver, extractPackageName(typeFqn), pluginId)
    return metadataStorage.getMetadataByTypeFqn(typeFqn)
  }
}

private fun extractPackageName(typeFqn: String): String = typeFqn.substringBeforeLast('.', "")