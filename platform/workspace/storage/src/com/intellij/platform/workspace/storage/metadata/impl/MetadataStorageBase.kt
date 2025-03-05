// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.impl

import com.intellij.platform.workspace.storage.metadata.MetadataHash
import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.utils.collectTypesByFqn

public abstract class MetadataStorageBase(
  private val metadataByTypeFqn: MutableMap<String, StorageTypeMetadata> = hashMapOf(),
  private val metadataHashByTypeFqn: MutableMap<String, MetadataHash> = hashMapOf()
): MetadataStorage {
  @Volatile
  private var metadataIsInitialized: Boolean = false
  @Volatile
  private var hashMetadataIsInitialized: Boolean = false

  final override fun getMetadataByTypeFqnOrNull(fqName: String): StorageTypeMetadata? {
    if (!metadataIsInitialized) {
      internalInitializeMetadata()
    }
    return metadataByTypeFqn[fqName]
  }

  final override fun getMetadataHashByTypeFqnOrNull(fqName: String): MetadataHash? {
    if (!hashMetadataIsInitialized) {
      internalInitializeMetadataHash()
    }
    return metadataHashByTypeFqn[fqName]
  }

  protected abstract fun initializeMetadata()

  @Synchronized
  private fun internalInitializeMetadata() {
    if (metadataIsInitialized) return
    initializeMetadata()
    metadataIsInitialized = true
  }

  protected abstract fun initializeMetadataHash()

  @Synchronized
  private fun internalInitializeMetadataHash() {
    if (hashMetadataIsInitialized) return
    initializeMetadataHash()
    hashMetadataIsInitialized = true
  }

  protected fun addMetadata(typeMetadata: StorageTypeMetadata) {
    typeMetadata.collectTypesByFqn(metadataByTypeFqn)
  }

  protected fun addMetadataHash(typeFqn: String, metadataHash: MetadataHash) {
    metadataHashByTypeFqn[typeFqn] = metadataHash
  }
}
