// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.model

import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId.ConnectionType
import com.intellij.platform.workspace.storage.metadata.StorageMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


/**
 * Stores metadata for the [WorkspaceEntity] properties.
 *
 * An entity may have properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [VirtualFileUrl];
 * * [WorkspaceEntity] or [SymbolicEntityId];
 * * [List] of another allowed type;
 * * [Map] of another allowed types where key is NOT a WorkspaceEntity;
 * * another data class with properties of the allowed types (references to entities must be wrapped into [EntityPointer]);
 * * sealed class where all implementations satisfy these requirements.
 */
public sealed interface PropertyMetadata: StorageMetadata {
  public val name: String
  public val valueType: ValueTypeMetadata
  public val isOpen: Boolean
  public val isComputable: Boolean
  public val withDefault: Boolean
}

public data class OwnPropertyMetadata(
  override val name: String,
  override val valueType: ValueTypeMetadata,
  override val isOpen: Boolean,
  override val isComputable: Boolean,
  override val withDefault: Boolean,
  val isKey: Boolean // EqualsBy annotation
): PropertyMetadata

public data class ExtPropertyMetadata(
  override val name: String,
  override val valueType: ValueTypeMetadata.EntityReference,
  override val isOpen: Boolean,
  override val isComputable: Boolean,
  override val withDefault: Boolean,
  val receiverFqn: String,
): PropertyMetadata


/**
 * Implements the value type metadata.
 *
 * Both parametrized types and primitive types are supported.
 *
 * E.g.:
 * * @Child val moduleEntity: ModuleEntity --> Entity(entityName = "ModuleEntity", isChild = false, connectionType = ConnectionType.ONE_TO_ONE, isNullable = false)
 * *
 * * List<CustomSealedClass>? --> ParameterizedType(
 * *                                primitive = KnownType(type = "List", isNullable = true),
 * *                                generics = listOf(CustomClass(classMetadata = 'Metadata class for CustomSealedClass', isNullable = false)
 * *                              )
 * *
 * * CustomDataClass<Int?, SymbolicEntityId<ArtifactEntity>>? --> ParameterizedType(
 * *                              primitive = CustomClass(classMetadata = 'Metadata class for CustomDataClass', isNullable = true),
 * *                              generics = listOf(
 * *                                  KnownType(type = "Int", isNullable = true),
 * *                                  ParameterizedType(
 * *                                      primitive = KnownType(type = "SymbolicEntityId", isNullable = false),
 * *                                      generics = listOf(KnownType(type = "ArtifactEntity", isNullable = false))
 * *                                  )
 * *                              )
 * *                           )
 */
public sealed class ValueTypeMetadata: StorageMetadata {

  public data class ParameterizedType(val primitive: SimpleType, val generics: List<ValueTypeMetadata>): ValueTypeMetadata()

  public sealed class SimpleType: ValueTypeMetadata(), NullableType {
    public data class PrimitiveType(val type: String, override val isNullable: Boolean): SimpleType()

    public data class CustomType(val typeMetadata: StorageClassMetadata, override val isNullable: Boolean): SimpleType()
  }

  public data class EntityReference(
    val entityFqName: String,
    val isChild: Boolean,
    val connectionType: ConnectionType,
    override val isNullable: Boolean
  ): ValueTypeMetadata(), NullableType

  public sealed interface NullableType {
    public val isNullable: Boolean
  }
}