package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields

fun getRefsConnectionId(objProperty: ObjProperty<*, *>): String {
  return if ((objProperty is ExtProperty<*, *>) && objProperty.name == "parent") {
    val originalField = objProperty.receiver.refsFields.first { it.valueType.javaType == objProperty.valueType.javaType }
    "${originalField.name.uppercase()}_CONNECTION_ID"
  }
  else "${objProperty.name.uppercase()}_CONNECTION_ID"
}

val ObjProperty<*, *>.refsConnectionIdCode: String
  get() = buildString {
    val ref = valueType.getRefType()
    append("internal val ${getRefsConnectionId(this@refsConnectionIdCode)}: ${ConnectionId} = ConnectionId.create(")
    if (ref.child) {
      append("${receiver.javaFullName}::class.java, ${ref.javaType}::class.java,")
    }
    else {
      append("${ref.javaType}::class.java, ${receiver.javaFullName}::class.java,")
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

fun ObjProperty<*, *>.refsConnectionMethodCode(builder: Boolean = false): String {
  val ref = valueType.getRefType()
  val connectionName = name.uppercase() + "_CONNECTION_ID"
  val getterName = if (ref.child) {
      "${Instrumentation.getOneChild}"
  }
  else {
    val valueType = referencedField.valueType.let { if (it is ValueType.Optional<*>) it.type else it }
    if (valueType !is ValueType.List<*> && valueType !is ValueType.ObjRef<*>) {
      error("Unsupported reference type")
    }
    if (builder) {
      "${Instrumentation.getParentBuilder}"
    } else {
      "${Instrumentation.getParent}"
    }
  }
  return "$getterName($connectionName, this)"
}