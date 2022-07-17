package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.getRefType
import com.intellij.workspaceModel.codegen.refsFields
import com.intellij.workspaceModel.codegen.utils.*
import com.intellij.workspaceModel.codegen.deft.TList
import com.intellij.workspaceModel.codegen.deft.TOptional
import com.intellij.workspaceModel.codegen.deft.TRef
import com.intellij.workspaceModel.codegen.deft.ValueType
import com.intellij.workspaceModel.codegen.deft.Field
import com.intellij.workspaceModel.codegen.deft.MemberOrExtField

val MemberOrExtField<*, *>.refsConnectionId: String
  get() = if (name == "parent") {
    val originalField = owner.structure.refsFields.first { it.type.javaType == type.javaType }
    "${originalField.name.uppercase()}_CONNECTION_ID"
  }
  else "${name.uppercase()}_CONNECTION_ID"

val MemberOrExtField<*, *>.refsConnectionIdCode: String
  get() = buildString {
    val ref = type.getRefType()
    val isListType = type is TList<*> || ((type as? TOptional<*>)?.type is TList<*>)

    append("internal val $refsConnectionId: ${ConnectionId::class.fqn} = ConnectionId.create(")
    if (ref.child) {
      append("${owner.name}::class.java, ${ref.javaType}::class.java,")
    }
    else {
      append("${ref.javaType}::class.java, ${owner.name}::class.java,")
    }
    val isParentNullable = if (ref.child) {
      if (isListType) {
        if (ref.targetObjType.abstract) {
          append(" ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,")
        }
        else {
          append(" ConnectionId.ConnectionType.ONE_TO_MANY,")
        }
      }
      else {
        if (ref.targetObjType.abstract) {
          append(" ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,")
        }
        else {
          append(" ConnectionId.ConnectionType.ONE_TO_ONE,")
        }
      }
      referencedField.type is TOptional<*>
    }
    else {
      val declaredReferenceFromParent = referencedField
      var valueType = declaredReferenceFromParent.type
      if (valueType is TOptional<*>) {
        valueType = valueType.type as ValueType<Any?>
      }
      if (valueType is TList<*>) {
        if (owner.abstract) {
          append(" ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,")
        }
        else {
          append(" ConnectionId.ConnectionType.ONE_TO_MANY,")
        }
      }
      else if (valueType is TRef<*>) {
        if (owner.abstract) {
          append(" ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,")
        }
        else {
          append(" ConnectionId.ConnectionType.ONE_TO_ONE,")
        }
      }
      type is TOptional<*>
    }
    append(" $isParentNullable)")
  }

fun Field<*, *>.refsConnectionMethodCode(genericType: String = ""): String {
  val ref = type.getRefType()
  val connectionName = name.uppercase() + "_CONNECTION_ID"
  val getterName = if (ref.child) {
    if (ref.targetObjType.abstract)
      "${fqn1(EntityStorage::extractOneToAbstractOneChild)}$genericType"
    else
      "${fqn1(EntityStorage::extractOneToOneChild)}$genericType"
  }
  else {
    var valueType = referencedField.type
    if (valueType is TOptional<*>) {
      valueType = valueType.type as ValueType<Any?>
    }
    when (valueType) {
      is TList<*> -> if (owner.abstract)
        "${fqn1(EntityStorage::extractOneToAbstractManyParent)}$genericType"
      else
        "${fqn1(EntityStorage::extractOneToManyParent)}$genericType"
      is TRef<*> -> if (owner.abstract)
        "${fqn1(EntityStorage::extractOneToAbstractOneParent)}$genericType"
      else
        "${fqn1(EntityStorage::extractOneToOneParent)}$genericType"
      else -> error("Unsupported reference type")
    }
  }
  return "$getterName($connectionName, this)"
}