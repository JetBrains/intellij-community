package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.utils.*

data class RefMethods(val getter: QualifiedName, val setter: QualifiedName)

infix fun QualifiedName.getterWithSetter(setter: QualifiedName): RefMethods = RefMethods(this, setter)

fun ObjProperty<*, *>.refNames(): RefMethods {
  if (!valueType.isRefType()) error("Call this on ref field")
  return when (valueType) {
    is ValueType.ObjRef -> constructCode(valueType)
    is ValueType.Optional -> constructCode((this.valueType as ValueType.Optional<*>).type)
    is ValueType.List<*> -> fqn2(EntityStorage::extractOneToManyChildren) getterWithSetter fqn4(EntityStorage::updateOneToManyChildrenOfParent)
    else -> error("Call this on ref field")
  }
}

private fun ObjProperty<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as ValueType.ObjRef<*>

  return if (type.child) {
    if (type.target.openness.extendable) {
      fqn1(EntityStorage::extractOneToAbstractOneChild) getterWithSetter fqn3(EntityStorage::updateOneToAbstractOneChildOfParent)
    }
    else {
      fqn1(EntityStorage::extractOneToOneChild) getterWithSetter fqn3(EntityStorage::updateOneToOneChildOfParent)
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
          fqn1(EntityStorage::extractOneToAbstractManyParent) getterWithSetter fqn3(EntityStorage::updateOneToAbstractManyParentOfChild)
        }
        else {
          fqn1(EntityStorage::extractOneToManyParent) getterWithSetter fqn3(EntityStorage::updateOneToManyParentOfChild)
        }
      }
      is ValueType.ObjRef<*> -> {
        if (receiver.openness.extendable) {
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
