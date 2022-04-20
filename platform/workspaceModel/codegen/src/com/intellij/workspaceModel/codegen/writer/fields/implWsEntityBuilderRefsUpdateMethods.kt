package org.jetbrains.deft.codegen.ijws.fields

import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.codegen.ijws.wsFqn
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
    /// XXX: it should be updateOneToManyChildrenOfParent, but this method is used now for simplification
    is TList<*> -> wsFqn("extractOneToManyChildren") getterWithSetter wsFqn("updateOneToManyChildrenOfParentById")
    else -> error("Call this on ref field")
  }
}

private fun Field<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as TRef<*>

  return if (type.child) {
    if (type.targetObjType.abstract) {
      wsFqn("extractOneToAbstractOneChild") getterWithSetter wsFqn("updateOneToAbstractOneChildOfParent")
    }
    else {
      wsFqn("extractOneToOneChild") getterWithSetter wsFqn("updateOneToOneChildOfParent")
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
          wsFqn("extractOneToAbstractManyParent") getterWithSetter wsFqn("updateOneToAbstractManyParentOfChild")
        }
        else {
          wsFqn("extractOneToManyParent") getterWithSetter wsFqn("updateOneToManyParentOfChild")
        }
      }
      is TRef<*> -> {
        if (owner.abstract) {
          wsFqn("extractOneToAbstractOneParent") getterWithSetter wsFqn("updateOneToAbstractOneParentOfChild")
        }
        else {
          wsFqn("extractOneToOneParent") getterWithSetter wsFqn("updateOneToOneParentOfChild")
        }
      }
      else -> error("Unsupported reference type")
    }
  }
}
