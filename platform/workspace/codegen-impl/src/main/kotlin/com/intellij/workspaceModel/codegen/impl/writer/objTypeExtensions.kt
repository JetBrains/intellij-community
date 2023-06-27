// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty

val ObjClass<*>.isStandardInterface: Boolean
  get() = name in setOf(WorkspaceEntity.simpleName, WorkspaceEntityWithSymbolicId.simpleName)

val ObjClass<*>.allSuperClasses: List<ObjClass<*>>
  get() = superTypes.filterIsInstance<ObjClass<*>>().flatMapTo(LinkedHashSet()) { it.allSuperClasses + listOf(it) }.toList() 

val ObjClass<*>.allFields: List<OwnProperty<*, *>>
  get() {
    val fieldsByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectFields(this, fieldsByName)
    return fieldsByName.values.toList() 
  }

private fun collectFields(objClass: ObjClass<*>, fieldsByName: MutableMap<String, OwnProperty<*, *>>) {
  for (superType in objClass.superTypes) {
    if (superType is ObjClass<*>) {
      collectFields(superType, fieldsByName)
    }
  }
  for (field in objClass.fields) {
    if (field.valueKind !is ObjProperty.ValueKind.Computable) {
      fieldsByName.remove(field.name)
      fieldsByName[field.name] = field
    }
  }
}

val ObjProperty<*, *>.hasSetter: Boolean
  get() = open || valueKind == ObjProperty.ValueKind.Plain

val ObjProperty<*, *>.javaName: String
  get() = name

val ObjProperty<*, *>.isOverride: Boolean
  get() = receiver.allSuperClasses.any { name in it.fieldsByName }