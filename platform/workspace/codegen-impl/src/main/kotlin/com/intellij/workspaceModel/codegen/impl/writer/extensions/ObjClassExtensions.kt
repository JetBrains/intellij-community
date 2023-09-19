// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.impl.writer.QualifiedName
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityWithSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.fqn

internal val ObjClass<*>.javaFullName: QualifiedName
  get() = fqn(module.name, name)

internal val ObjClass<*>.javaBuilderName: String
  get() = "$name.Builder"

internal val ObjClass<*>.javaImplName: String
  get() = "${name.replace(".", "")}Impl"

internal val ObjClass<*>.javaImplBuilderName
  get() = "${javaImplName}.Builder"


internal val ObjClass<*>.isAbstract: Boolean
  get() = openness == ObjClass.Openness.abstract


internal val ObjClass<*>.isStandardInterface: Boolean
  get() = name in setOf(WorkspaceEntity.simpleName, WorkspaceEntityWithSymbolicId.simpleName)

internal val ObjClass<*>.allSuperClasses: List<ObjClass<*>>
  get() = superTypes.filterIsInstance<ObjClass<*>>().flatMapTo(LinkedHashSet()) { it.allSuperClasses + listOf(it) }.toList()

internal val ObjClass<*>.allFields: List<OwnProperty<*, *>>
  get() {
    val fieldsByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectFields(this, fieldsByName, false)
    return fieldsByName.values.toList() 
  }

internal val ObjClass<*>.allFieldsWithComputable: List<OwnProperty<*, *>>
  get() {
    val fieldsByName = LinkedHashMap<String, OwnProperty<*, *>>()
    collectFields(this, fieldsByName, true)
    return fieldsByName.values.toList()
  }

private fun collectFields(objClass: ObjClass<*>, fieldsByName: MutableMap<String, OwnProperty<*, *>>, withComputable: Boolean) {
  for (superType in objClass.superTypes) {
    if (superType is ObjClass<*>) {
      collectFields(superType, fieldsByName, withComputable)
    }
  }
  for (field in objClass.fields) {
    if (withComputable || field.valueKind !is ObjProperty.ValueKind.Computable) {
      fieldsByName.remove(field.name)
      fieldsByName[field.name] = field
    }
  }
}


internal val ObjClass<*>.builderWithTypeParameter: Boolean
  get() = openness.extendable
