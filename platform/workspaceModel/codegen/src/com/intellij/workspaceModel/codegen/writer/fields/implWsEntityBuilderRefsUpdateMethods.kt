package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.utils.fqn1
import com.intellij.workspaceModel.codegen.utils.fqn2
import com.intellij.workspaceModel.codegen.utils.fqn3
import com.intellij.workspaceModel.codegen.utils.fqn4
import com.intellij.workspaceModel.codegen.deft.TList
import com.intellij.workspaceModel.codegen.deft.TOptional
import com.intellij.workspaceModel.codegen.deft.TRef
import com.intellij.workspaceModel.codegen.deft.ValueType
import com.intellij.workspaceModel.codegen.deft.Field

data class RefMethods(val getter: String, val setter: String)

infix fun String.getterWithSetter(setter: String): RefMethods = RefMethods(this, setter)

fun Field<*, *>.refNames(): RefMethods {
  if (!type.isRefType()) error("Call this on ref field")
  return when (type) {
    is TRef -> constructCode(type)
    is TOptional -> constructCode((this.type as TOptional<*>).type)
    is TList<*> -> fqn2(EntityStorage::extractOneToManyChildren) getterWithSetter fqn4(EntityStorage::updateOneToManyChildrenOfParent)
    else -> error("Call this on ref field")
  }
}

private fun Field<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as TRef<*>

  return if (type.child) {
    if (type.targetObjType.abstract) {
      fqn1(EntityStorage::extractOneToAbstractOneChild) getterWithSetter fqn3(EntityStorage::updateOneToAbstractOneChildOfParent)
    }
    else {
      fqn1(EntityStorage::extractOneToOneChild) getterWithSetter fqn3(EntityStorage::updateOneToOneChildOfParent)
    }
  }
  else {
    var valueType = referencedField.type
    if (valueType is TOptional<*>) {
      valueType = valueType.type as ValueType<Any?>
    }
    when (valueType) {
      is TList<*> -> {
        if (owner.abstract) {
          fqn1(EntityStorage::extractOneToAbstractManyParent) getterWithSetter fqn3(EntityStorage::updateOneToAbstractManyParentOfChild)
        }
        else {
          fqn1(EntityStorage::extractOneToManyParent) getterWithSetter fqn3(EntityStorage::updateOneToManyParentOfChild)
        }
      }
      is TRef<*> -> {
        if (owner.abstract) {
          fqn1(EntityStorage::extractOneToAbstractOneParent) getterWithSetter fqn3(EntityStorage::updateOneToAbstractOneParentOfChild)
        }
        else {
          fqn1(EntityStorage::extractOneToOneParent) getterWithSetter fqn3(EntityStorage::updateOneToOneParentOfChild)
        }
      }
      else -> error("Unsupported reference type")
    }
  }
}
