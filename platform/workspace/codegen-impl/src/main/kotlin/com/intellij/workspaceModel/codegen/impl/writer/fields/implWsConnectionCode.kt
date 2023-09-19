package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields

val ObjProperty<*, *>.refsConnectionId: String
  get() = if (name == "parent") {
    val originalField = receiver.refsFields.first { it.valueType.javaType == valueType.javaType }
    "${originalField.name.uppercase()}_CONNECTION_ID"
  }
  else "${name.uppercase()}_CONNECTION_ID"

val ObjProperty<*, *>.refsConnectionIdCode: String
  get() = buildString {
    val ref = valueType.getRefType()
    append("internal val $refsConnectionId: ${ConnectionId} = ConnectionId.create(")
    if (ref.child) {
      append("${receiver.name}::class.java, ${ref.javaType}::class.java,")
    }
    else {
      append("${ref.javaType}::class.java, ${receiver.name}::class.java,")
    }
    val connectionType = this@refsConnectionIdCode.refsConnectionType(ref)
    if (connectionType.isNotEmpty()) {
      append(" $connectionType,")
    }
    val isParentNullable = (ref.child && referencedField.valueType is ValueType.Optional<*>) || (!ref.child && this@refsConnectionIdCode.valueType is ValueType.Optional<*>)
    append(" $isParentNullable)")
  }

fun ObjProperty<*, *>.refsConnectionType(ref: ValueType.ObjRef<*>): String {
  val isListType = valueType is ValueType.List<*> || ((valueType as? ValueType.Optional<*>)?.type is ValueType.List<*>)
  if (ref.child) {
    return "$ConnectionId.ConnectionType.${
      if (isListType) {
        if (ref.target.openness.extendable) "ONE_TO_ABSTRACT_MANY" else "ONE_TO_MANY"
      } else {
        if (ref.target.openness.extendable) "ABSTRACT_ONE_TO_ONE" else "ONE_TO_ONE"
      }
    }"
  }

  val declaredReferenceFromParent = referencedField
  var valueType = declaredReferenceFromParent.valueType
  if (valueType is ValueType.Optional<*>) {
    valueType = valueType.type
  }
  if (valueType is ValueType.List<*>) {
    return "$ConnectionId.ConnectionType.${if (receiver.openness.extendable) "ONE_TO_ABSTRACT_MANY" else "ONE_TO_MANY"}"
  } else if (valueType is ValueType.ObjRef<*>) {
    return "$ConnectionId.ConnectionType.${if (receiver.openness.extendable) "ABSTRACT_ONE_TO_ONE" else "ONE_TO_ONE"}"
  }
  return ""
}

fun ObjProperty<*, *>.refsConnectionMethodCode(genericType: String = ""): String {
  val ref = valueType.getRefType()
  val connectionName = name.uppercase() + "_CONNECTION_ID"
  val getterName = if (ref.child) {
    if (ref.target.openness.extendable)
      "${EntityStorage.extractOneToAbstractOneChild}$genericType"
    else
      "${EntityStorage.extractOneToOneChild}$genericType"
  }
  else {
    var valueType = referencedField.valueType
    if (valueType is ValueType.Optional<*>) {
      valueType = valueType.type
    }
    when (valueType) {
      is ValueType.List<*> -> if (receiver.openness.extendable)
        "${EntityStorage.extractOneToAbstractManyParent}$genericType"
      else
        "${EntityStorage.extractOneToManyParent}$genericType"
      is ValueType.ObjRef<*> -> if (receiver.openness.extendable)
        "${EntityStorage.extractOneToAbstractOneParent}$genericType"
      else
        "${EntityStorage.extractOneToOneParent}$genericType"
      else -> error("Unsupported reference type")
    }
  }
  return "$getterName($connectionName, this)"
}