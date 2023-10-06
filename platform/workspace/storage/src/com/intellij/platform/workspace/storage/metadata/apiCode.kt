// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.metadata.exceptions.MissingTypeMetadataException
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata

/**
 * Encapsulates all classes that store metadata for classes in [EntityStorage]
 *
 * See [com.intellij.platform.workspace.storage.metadata.model] to see all the classes that implement this interface
 */
public interface StorageMetadata


/**
 * Metadata storage, whose implementation is generated with entities implementation
 *
 * Stores metadata of all types from one package
 */
public interface MetadataStorage {
  public fun getMetadataByTypeFqnOrNull(fqName: String): StorageTypeMetadata?

  public fun getMetadataByTypeFqn(fqName: String): StorageTypeMetadata =
    getMetadataByTypeFqnOrNull(fqName) ?: throw MissingTypeMetadataException(fqName)
}