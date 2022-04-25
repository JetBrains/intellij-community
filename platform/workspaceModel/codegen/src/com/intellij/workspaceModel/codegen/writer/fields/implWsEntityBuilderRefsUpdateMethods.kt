package org.jetbrains.deft.codegen.ijws.fields

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.impl.*
import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.codegen.utils.fqn1
import org.jetbrains.deft.codegen.utils.fqn2
import org.jetbrains.deft.codegen.utils.fqn3
import org.jetbrains.deft.codegen.utils.fqn4
import org.jetbrains.deft.impl.TList
import org.jetbrains.deft.impl.TOptional
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.ValueType
import org.jetbrains.deft.impl.fields.Field

data class RefMethods(val getter: String, val setter: String)

infix fun String.getterWithSetter(setter: String): RefMethods = RefMethods(this, setter)

fun Field<*, *>.refNames(): RefMethods {
  if (!type.isRefType()) error("Call this on ref field")
  return when (type) {
    is TRef -> constructCode(type)
    is TOptional -> constructCode((this.type as TOptional<*>).type)
    is TList<*> -> fqn2(WorkspaceEntityStorage::extractOneToManyChildren) getterWithSetter fqn4(WorkspaceEntityStorage::updateOneToManyChildrenOfParent)
    else -> error("Call this on ref field")
  }
}

private fun Field<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as TRef<*>

  return if (type.child) {
    if (type.targetObjType.abstract) {
      fqn1(WorkspaceEntityStorage::extractOneToAbstractOneChild) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToAbstractOneChildOfParent)
    }
    else {
      fqn1(WorkspaceEntityStorage::extractOneToOneChild) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToOneChildOfParent)
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
          fqn1(WorkspaceEntityStorage::extractOneToAbstractManyParent) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToAbstractManyParentOfChild)
        }
        else {
          fqn1(WorkspaceEntityStorage::extractOneToManyParent) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToManyParentOfChild)
        }
      }
      is TRef<*> -> {
        if (owner.abstract) {
          fqn1(WorkspaceEntityStorage::extractOneToAbstractOneParent) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToAbstractOneParentOfChild)
        }
        else {
          fqn1(WorkspaceEntityStorage::extractOneToOneParent) getterWithSetter fqn3(WorkspaceEntityStorage::updateOneToOneParentOfChild)
        }
      }
      else -> error("Unsupported reference type")
    }
  }
}
