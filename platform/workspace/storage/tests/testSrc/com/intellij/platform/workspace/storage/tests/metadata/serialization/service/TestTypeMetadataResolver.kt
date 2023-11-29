// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization.service

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.metadata.MetadataHash
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.tests.metadata.serialization.deserialization
import com.intellij.platform.workspace.storage.tests.metadata.serialization.replaceCacheVersion

internal class TestTypeMetadataResolver(
  private val typeMetadataResolver: TypeMetadataResolver
): TypeMetadataResolver {
  override fun resolveTypeMetadata(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): StorageTypeMetadata {
    return typeMetadataResolver.resolveTypeMetadata(typesResolver, processTypeFqn(typeFqn), pluginId)
  }

  override fun resolveTypeMetadataHashOrNull(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash? {
    return typeMetadataResolver.resolveTypeMetadataHashOrNull(typesResolver, processTypeFqn(typeFqn), pluginId)
  }

  override fun resolveTypeMetadataHash(typesResolver: EntityTypesResolver, typeFqn: String, pluginId: PluginId): MetadataHash {
    return typeMetadataResolver.resolveTypeMetadataHash(typesResolver, processTypeFqn(typeFqn), pluginId)
  }


  private fun processTypeFqn(typeFqn: String): String = if (deserialization) typeFqn.replaceCacheVersion() else typeFqn
}