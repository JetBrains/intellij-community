// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.utils

import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.SimpleType.CustomType


internal fun StorageTypeMetadata.collectClasses(
  classes: MutableMap<String, StorageClassMetadata> = hashMapOf()
): List<StorageClassMetadata> {
  return collectClassesByFqn(classes).values.toList()
}

internal fun StorageTypeMetadata.collectClassesByFqn(
  classes: MutableMap<String, StorageClassMetadata> = hashMapOf()
): Map<String, StorageClassMetadata> {
  recursiveTypeFinder(this, classes) { type ->
    type is StorageClassMetadata && type !is FinalClassMetadata.KnownClass
  }
  return classes
}


@Suppress("UNCHECKED_CAST")
private fun <T> recursiveTypeFinder(type: StorageTypeMetadata, types: MutableMap<String, T>,
                                    valueSelector: (StorageTypeMetadata) -> Boolean) {
  if (types.containsKey(type.fqName)) {
    return
  }

  if (valueSelector.invoke(type)) {
    types[type.fqName] = type as T
  }

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
    .forEach { recursiveTypeFinder(it.typeMetadata, types, valueSelector) }


  if (type is ExtendableClassMetadata) {
    type.subclasses.forEach { recursiveTypeFinder(it, types, valueSelector) }
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


