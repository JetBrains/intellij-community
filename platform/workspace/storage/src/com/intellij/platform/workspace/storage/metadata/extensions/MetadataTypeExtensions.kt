// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.extensions

import com.intellij.platform.workspace.storage.metadata.StorageMetadata
import com.intellij.platform.workspace.storage.metadata.model.*

private const val UNKNOWN_METADATA_TYPE = "Unknown metadata type"

internal val StorageMetadata.metadataType: String
  get() = when (this) {
    is StorageTypeMetadata -> this.metadataType
    is PropertyMetadata -> this.metadataType
    is ValueTypeMetadata -> this.metadataType
    else -> UNKNOWN_METADATA_TYPE
  }

private val StorageTypeMetadata.metadataType: String
  get() = when (this) {
    is EntityMetadata -> "Entity"
    is FinalClassMetadata.ClassMetadata -> "Final class"
    is FinalClassMetadata.ObjectMetadata -> "Object"
    is FinalClassMetadata.EnumClassMetadata -> "Enum class"
    is ExtendableClassMetadata.AbstractClassMetadata -> "Abstract class"
    else -> UNKNOWN_METADATA_TYPE
  }

private val PropertyMetadata.metadataType: String
  get() = when (this) {
    is OwnPropertyMetadata -> "Own property"
    is ExtPropertyMetadata -> "Extension property"
  }

private val ValueTypeMetadata.metadataType: String
  get() = when (this) {
    is ValueTypeMetadata.ParameterizedType -> "Parametrized type"
    is ValueTypeMetadata.EntityReference -> "Entity reference"
    is ValueTypeMetadata.SimpleType.PrimitiveType -> "Primitive type"
    is ValueTypeMetadata.SimpleType.CustomType -> "Custom type"
  }