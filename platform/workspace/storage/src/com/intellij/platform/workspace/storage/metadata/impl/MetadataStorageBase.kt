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
  private var metadataIsInitialized: Boolean = false
  private var hashMetadataIsInitialized: Boolean = false

  override fun getMetadataByTypeFqnOrNull(fqName: String): StorageTypeMetadata? {
    if (!metadataIsInitialized) {
      metadataIsInitialized = true
      initializeMetadata()
    }
    return metadataByTypeFqn[fqName]
  }

  override fun getMetadataHashByTypeFqnOrNull(fqName: String): MetadataHash? {
    if (!hashMetadataIsInitialized) {
      hashMetadataIsInitialized = true
      initializeMetadataHash()
    }
    return metadataHashByTypeFqn[fqName]
  }

  protected abstract fun initializeMetadata()

  protected abstract fun initializeMetadataHash()

  protected fun addMetadata(typeMetadata: StorageTypeMetadata) {
    typeMetadata.collectTypesByFqn(metadataByTypeFqn)
  }

  protected fun addMetadataHash(typeFqn: String, metadataHash: MetadataHash) {
    metadataHashByTypeFqn[typeFqn] = metadataHash
  }
}

