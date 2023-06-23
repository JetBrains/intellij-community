package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.impl.writer.isRefType
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.*

data class RefMethods(val getter: QualifiedName, val setter: QualifiedName)

infix fun QualifiedName.getterWithSetter(setter: QualifiedName): RefMethods = RefMethods(this, setter)

fun ObjProperty<*, *>.refNames(): RefMethods {
  if (!valueType.isRefType()) error("Call this on ref field")
  return when (valueType) {
    is ValueType.ObjRef -> constructCode(valueType)
    is ValueType.Optional -> constructCode((this.valueType as ValueType.Optional<*>).type)
    is ValueType.List<*> -> EntityStorage.extractOneToManyChildren getterWithSetter EntityStorage.updateOneToManyChildrenOfParent
    else -> error("Call this on ref field")
  }
}

private fun ObjProperty<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as ValueType.ObjRef<*>

  return if (type.child) {
    if (type.target.openness.extendable) {
      EntityStorage.extractOneToAbstractOneChild getterWithSetter EntityStorage.updateOneToAbstractOneChildOfParent
    }
    else {
      EntityStorage.extractOneToOneChild getterWithSetter EntityStorage.updateOneToOneChildOfParent
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
          EntityStorage.extractOneToAbstractManyParent getterWithSetter EntityStorage.updateOneToAbstractManyParentOfChild
        }
        else {
          EntityStorage.extractOneToManyParent getterWithSetter EntityStorage.updateOneToManyParentOfChild
        }
      }
      is ValueType.ObjRef<*> -> {
        if (receiver.openness.extendable) {
          EntityStorage.extractOneToAbstractOneParent getterWithSetter EntityStorage.updateOneToAbstractOneParentOfChild
        }
        else {
          EntityStorage.extractOneToOneParent getterWithSetter EntityStorage.updateOneToOneParentOfChild
        }
      }
      else -> error("Unsupported reference type")
    }
  }
}
