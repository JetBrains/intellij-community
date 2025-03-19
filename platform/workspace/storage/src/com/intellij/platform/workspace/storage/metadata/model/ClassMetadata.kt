// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.model

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.metadata.StorageMetadata

/**
 * Common metadata interface for the [WorkspaceEntity] and its properties.
 */
public sealed interface StorageTypeMetadata: StorageMetadata {
  public val fqName: String
  public val properties: List<OwnPropertyMetadata> // List must be used, because the order of properties is important for deserialization
  public val supertypes: List<String> //supertypes fqn names
}


/**
 * Stores the [WorkspaceEntity] metadata.
 *
 * It is [WorkspaceEntity] too.
 */
public data class EntityMetadata(
  override val fqName: String,
  override val properties: List<OwnPropertyMetadata>,
  override val supertypes: List<String>,
  val extProperties: List<ExtPropertyMetadata>,
  val entityDataFqName: String, //Entity data name of this entity
  val isAbstract: Boolean //Abstract annotation
) : StorageTypeMetadata


public sealed interface StorageClassMetadata: StorageTypeMetadata

/**
 * Implements metadata of final classes in the [EntityStorage].
 *
 * Final classes have properties and have no subclasses.
 *
 * Supported final class types in the storage:
 * * final classes
 * * objects
 * * enum entries
 */
public sealed class FinalClassMetadata: StorageClassMetadata {
  public data class ClassMetadata(
    override val fqName: String,
    override val properties: List<OwnPropertyMetadata>,
    override val supertypes: List<String>
  ) : FinalClassMetadata()

  public data class ObjectMetadata(
    override val fqName: String,
    override val properties: List<OwnPropertyMetadata>,
    override val supertypes: List<String>
  ) : FinalClassMetadata()

  public data class EnumClassMetadata(
    override val fqName: String,
    override val properties: List<OwnPropertyMetadata>,
    override val supertypes: List<String>,
    val values: List<String>
  ) : FinalClassMetadata()

  public data class KnownClass(override val fqName: String) : FinalClassMetadata() {
    override val properties: List<OwnPropertyMetadata>
      get() = emptyList()
    override val supertypes: List<String>
      get() = emptyList()
  }
}

/**
 * Implements metadata of abstract classes in the [EntityStorage].
 *
 * Abstract classes have subclasses and have no properties.
 */
public sealed class ExtendableClassMetadata: StorageClassMetadata {
  public abstract val subclasses: List<FinalClassMetadata>
  override val properties: List<OwnPropertyMetadata>
    get() = emptyList()

  public data class AbstractClassMetadata(
    override val fqName: String,
    override val supertypes: List<String>,
    override val subclasses: List<FinalClassMetadata>
  ) : ExtendableClassMetadata()
}






