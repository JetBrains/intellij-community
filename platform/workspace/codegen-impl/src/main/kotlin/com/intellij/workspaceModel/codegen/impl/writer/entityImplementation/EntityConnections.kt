package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.writer.ConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType

// TODO: we only sanitize "parent" connection, which is wrong. Write a test with name clash
fun GeneratorContext.connectionIdForReference(objProperty: ObjProperty<*, *>): String {
  return if ((objProperty is ExtProperty<*, *>) && objProperty.name == "parent") {
    val originalField = objProperty.receiver.refsFields.first { getJavaType(it) == getJavaType(objProperty) }
    "${originalField.name.uppercase()}_CONNECTION_ID"
  }
  else "${objProperty.name.uppercase()}_CONNECTION_ID"
}

fun CodeContext.connectionInitialization(objProperty: ObjProperty<*, *>) {
  val unwrappedType = unwrapReferenceType(objProperty.valueType)
  if (unwrappedType == null) {
    notReferenceError("connectionInitialization", objProperty)
    return
  }
  val (parentClass, childClass) = if (unwrappedType.child) {
    objProperty.receiver.javaFullName to getJavaType(objProperty, unwrappedType)
  }
  else {
    getJavaType(objProperty, unwrappedType) to objProperty.receiver.javaFullName
  }
  val connectionType = refsConnectionType(objProperty, unwrappedType)
  val isParentNullable =
    (unwrappedType.child && getReferencedField(objProperty)?.valueType is ValueType.Optional<*>) || (!unwrappedType.child && objProperty.valueType is ValueType.Optional<*>)
  val connectionId = connectionIdForReference(objProperty)

  +"internal val $connectionId: $ConnectionId = ConnectionId.create($parentClass::class.java, $childClass::class.java, $connectionType, $isParentNullable)"
}

fun GeneratorContext.refsConnectionType(objProperty: ObjProperty<*, *>, ref: ValueType.ObjRef<*>): String {
  val isListType =
    objProperty.valueType is ValueType.List<*> || ((objProperty.valueType as? ValueType.Optional<*>)?.type is ValueType.List<*>)
  if (ref.child) {
    return "$ConnectionId.ConnectionType.${
      if (isListType) {
        if (ref.target.openness.extendable) "ONE_TO_ABSTRACT_MANY" else "ONE_TO_MANY"
      }
      else {
        if (ref.target.openness.extendable) "ABSTRACT_ONE_TO_ONE" else "ONE_TO_ONE"
      }
    }"
  }

  val declaredReferenceFromParent = getReferencedField(objProperty)
  var valueType = declaredReferenceFromParent?.valueType
  if (valueType is ValueType.Optional<*>) {
    valueType = valueType.type
  }
  if (valueType is ValueType.List<*>) {
    return "$ConnectionId.ConnectionType.${if (objProperty.receiver.openness.extendable) "ONE_TO_ABSTRACT_MANY" else "ONE_TO_MANY"}"
  }
  else if (valueType is ValueType.ObjRef<*>) {
    return "$ConnectionId.ConnectionType.${if (objProperty.receiver.openness.extendable) "ABSTRACT_ONE_TO_ONE" else "ONE_TO_ONE"}"
  }
  return ""
}

fun GeneratorContext.refsConnectionMethodCode(objProperty: ObjProperty<*, *>, builder: Boolean = false): String {
  val unwrappedType = unwrapReferenceType(objProperty.valueType)
  if (unwrappedType == null) {
    notReferenceError("connection method", objProperty)
    return ""
  }
  val connectionName = connectionIdForReference(objProperty)
  val getterName = if (unwrappedType.child) {
    "${Instrumentation.getOneChild}"
  }
  else {
    val valueType = getReferencedField(objProperty)?.valueType.let { if (it is ValueType.Optional<*>) it.type else it }
    if (valueType !is ValueType.List<*> && valueType !is ValueType.ObjRef<*>) {
      reportPropertyError("Unsupported reference type: $valueType", objProperty)
      return ""
    }
    if (builder) {
      "${Instrumentation.getParentBuilder}"
    }
    else {
      "${Instrumentation.getParent}"
    }
  }
  return "$getterName($connectionName, this)"
}