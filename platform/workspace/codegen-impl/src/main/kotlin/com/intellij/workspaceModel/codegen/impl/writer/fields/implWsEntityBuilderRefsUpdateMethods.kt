// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType

data class RefMethods(
  val getter: QualifiedName,
  val getterBuilder: String,
  val setter: QualifiedName,
)

fun ObjProperty<*, *>.refNames(): RefMethods {
  if (!valueType.isRefType()) error("Call this on ref field")
  return when (valueType) {
    is ValueType.ObjRef -> constructCode(valueType)
    is ValueType.Optional -> constructCode((this.valueType as ValueType.Optional<*>).type)
    is ValueType.List<*> -> RefMethods(EntityStorage.extractOneToManyChildren, "getManyChildrenBuilders", EntityStorage.updateOneToManyChildrenOfParent)
    else -> error("Call this on ref field")
  }
}

private fun ObjProperty<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as ValueType.ObjRef<*>

  return if (type.child) {
    if (type.target.openness.extendable) {
      RefMethods(EntityStorage.extractOneToAbstractOneChild, "getOneChildBuilder", EntityStorage.updateOneToAbstractOneChildOfParent)
    }
    else {
      RefMethods(EntityStorage.extractOneToOneChild, "getOneChildBuilder", EntityStorage.updateOneToOneChildOfParent)
    }
  }
  else {
    var valueType = referencedField.valueType
    if (valueType is ValueType.Optional<*>) {
      valueType = valueType.type
    }
    when (valueType) {
      is ValueType.List<*> -> {
        if (receiver.openness.extendable) {
          RefMethods(EntityStorage.extractOneToAbstractManyParent, "getParentBuilder", EntityStorage.updateOneToAbstractManyParentOfChild)
        }
        else {
          RefMethods(EntityStorage.extractOneToManyParent, "getParentBuilder", EntityStorage.updateOneToManyParentOfChild)
        }
      }
      is ValueType.ObjRef<*> -> {
        if (receiver.openness.extendable) {
          RefMethods(EntityStorage.extractOneToAbstractOneParent, "getParentBuilder", EntityStorage.updateOneToAbstractOneParentOfChild)
        }
        else {
          RefMethods(EntityStorage.extractOneToOneParent, "getParentBuilder", EntityStorage.updateOneToOneParentOfChild)
        }
      }
      else -> error("Unsupported reference type")
    }
  }
}
