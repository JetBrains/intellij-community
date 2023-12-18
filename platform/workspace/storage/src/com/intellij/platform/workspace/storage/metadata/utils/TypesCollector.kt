// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.utils

import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.metadata.MetadataStorage
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.SimpleType.CustomType
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver

internal fun StorageTypeMetadata.collectTypesByFqn(metadataStorage: MetadataStorage? = null): Map<String, StorageTypeMetadata> {
  val typesByFqn: MutableMap<String, StorageTypeMetadata> = hashMapOf()
  collectTypesByFqn(typesByFqn, metadataStorage)
  return typesByFqn
}

internal fun StorageTypeMetadata.collectTypesByFqn(types: MutableMap<String, StorageTypeMetadata>, metadataStorage: MetadataStorage? = null) {
  recursiveTypeFinder(this, types, metadataStorage)
}


private fun recursiveTypeFinder(type: StorageTypeMetadata, types: MutableMap<String, StorageTypeMetadata>, metadataStorage: MetadataStorage?) {
  if (types.containsKey(type.fqName)) {
    return
  }

  if (type is FinalClassMetadata.KnownClass) {
    if (metadataStorage != null) {
      val realTypeMetadata = TypeMetadataResolver.getInstance().resolveTypeMetadataOrNull(metadataStorage, type.fqName)
      if (realTypeMetadata != null) {
        recursiveTypeFinder(realTypeMetadata, types, metadataStorage)
      }
    }
    return
  }

  types[type.fqName] = type

  val valueTypes: MutableList<ValueTypeMetadata> = arrayListOf()
  type.properties.map { it.valueType }.forEach {
    if (it is ValueTypeMetadata.ParameterizedType) {
      valueTypes.add(it.primitive)
      it.findNotParametrizedGenerics(valueTypes)
    } else {
      valueTypes.add(it)
    }
  }

  valueTypes.filterIsInstance<CustomType>()
    .forEach { recursiveTypeFinder(it.typeMetadata, types, metadataStorage) }


  if (type is ExtendableClassMetadata) {
    type.subclasses.forEach { recursiveTypeFinder(it, types, metadataStorage) }
  }
}

private fun ValueTypeMetadata.ParameterizedType.findNotParametrizedGenerics(simpleGenerics: MutableList<ValueTypeMetadata>) {
  generics.forEach {
    if (it is ValueTypeMetadata.ParameterizedType) {
      it.findNotParametrizedGenerics(simpleGenerics)
    } else {
      simpleGenerics.add(it)
    }
  }
}


