// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.resolver

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.serialization.PluginId
import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge
import com.intellij.platform.workspace.storage.metadata.exceptions.MissingMetadataStorage


internal object MetadataStorageResolver {
  private val metadataStorageCache: MutableMap<Pair<PluginId, String>, MetadataStorage?> = hashMapOf()

  private const val GENERATED_METADATA_STORAGE_IMPL_NAME = "MetadataStorageImpl"

  internal fun resolveMetadataStorage(typesResolver: EntityTypesResolver, packageName: String,
                                     pluginId: PluginId): MetadataStorage {
    val metadataStorage = metadataStorageCache.getOrPut(pluginId to packageName) {
      val metadataStorage = typesResolver.resolveClass(metadataStorageFqn(packageName), pluginId).metadataStorageInstance
      if (metadataStorage is MetadataStorageBridge) {
        metadataStorage.metadataStorage
      } else {
        metadataStorage
      }
    }
    return metadataStorage ?: throw MissingMetadataStorage(metadataStorageFqn(packageName))
  }


  private fun metadataStorageFqn(packageName: String): String = "$packageName.$GENERATED_METADATA_STORAGE_IMPL_NAME"
}

private val Class<*>.metadataStorageInstance: MetadataStorage?
  get() {
    val metadataStorageImpl = getDeclaredField("INSTANCE").get(null)
    return metadataStorageImpl as? MetadataStorage
  }