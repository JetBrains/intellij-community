// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.metadata.exceptions.MissingTypeMetadataException
import com.intellij.platform.workspace.storage.metadata.exceptions.MissingTypeMetadataHashException
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

  public fun getMetadataHashByTypeFqnOrNull(fqName: String): MetadataHash?

  public fun getMetadataByTypeFqn(fqName: String): StorageTypeMetadata =
    getMetadataByTypeFqnOrNull(fqName) ?: throw MissingTypeMetadataException(fqName)

  public fun getMetadataHashByTypeFqn(fqName: String): MetadataHash =
    getMetadataHashByTypeFqnOrNull(fqName) ?: throw MissingTypeMetadataHashException(fqName)
}


/**
 * Its implementation is generated with entities implementation in each package.
 * It is a bridge to [MetadataStorage] implementation, which stores metadata of all types from the entire module
 *
 * Used to speed up [EntityStorage] deserialization:
 * * During serialization [MetadataStorage] fqn's, that store metadata for the entire module, are saved in the cache.
 * * During deserialization we need to load for each module only one [MetadataStorage] whose fqn is stored in the cache.
 * In this way we speed up deserialization because we do not load so many [MetadataStorage] classes
 *
 * See [EntityStorageSerializerImpl]
 */
public abstract class MetadataStorageBridge(public val metadataStorage: MetadataStorage): MetadataStorage by metadataStorage


internal typealias MetadataHash = Int