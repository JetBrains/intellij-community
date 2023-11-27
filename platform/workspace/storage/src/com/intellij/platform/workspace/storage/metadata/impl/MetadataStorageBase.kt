// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.impl

import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.utils.collectTypesByFqn

public abstract class MetadataStorageBase(
  private val metadataByTypeFqn: MutableMap<String, StorageTypeMetadata> = hashMapOf()
): MetadataStorage {

  protected fun addMetadata(typeMetadata: StorageTypeMetadata) {
    typeMetadata.collectTypesByFqn(metadataByTypeFqn)
  }

  override fun getMetadataByTypeFqnOrNull(fqName: String): StorageTypeMetadata? = metadataByTypeFqn[fqName]
}

