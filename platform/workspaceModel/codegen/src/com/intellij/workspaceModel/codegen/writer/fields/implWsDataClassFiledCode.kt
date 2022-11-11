package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.writer.hasSetter
import com.intellij.workspaceModel.codegen.writer.isOverride
import com.intellij.workspaceModel.codegen.writer.javaName

val ObjProperty<*, *>.implWsDataFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) append(implWsBlockingCodeOverride)
      else append(implWsDataBlockingCode)
    }
    else {
      val expression = when (val kind = valueKind) {
        is ObjProperty.ValueKind.Computable -> kind.expression
        is ObjProperty.ValueKind.WithDefault -> kind.value
        else -> error(kind)
      }
      if (expression.startsWith("=")) {
        append("var $javaName: ${valueType.javaType} $expression")
      } else {
        append("var $javaName: ${valueType.javaType} = $expression")
      }
    }
  }
private val ObjProperty<*, *>.implWsDataBlockingCode: String
  get() = implWsDataBlockCode(valueType, name)

private fun ObjProperty<*, *>.implWsDataBlockCode(fieldType: ValueType<*>, name: String, isOptional: Boolean = false): String {
  return when (fieldType) {
    ValueType.Int -> "var $javaName: ${fieldType.javaType} = 0"
    ValueType.Boolean -> "var $javaName: ${fieldType.javaType} = false"
    ValueType.String -> "lateinit var $javaName: String"
    is ValueType.ObjRef<*> -> error("Reference type at EntityData not supported")
    is ValueType.Collection<*, *> -> {
      if (fieldType.isRefType()) error("Reference type at EntityData not supported")
      if (!isOptional) {
        "lateinit var $javaName: ${fieldType.javaMutableType}"
      }
      else {
        "var $javaName: ${fieldType.javaMutableType}? = null"
      }
    }
    is ValueType.Map<*, *> -> {
      if (!isOptional) {
        "lateinit var $javaName: ${fieldType.javaType}"
      }
      else {
        "var $javaName: ${fieldType.javaType}? = null"
      }
    }
    is ValueType.Optional<*> -> when (fieldType.type) {
      ValueType.Int, ValueType.Boolean, ValueType.String -> "var $javaName: ${fieldType.javaType} = null"
      else -> implWsDataBlockCode(fieldType.type, name, true)
    }
    is ValueType.JvmClass -> {
      if (!isOptional) {
        "lateinit var $javaName: ${fieldType.javaType}"
      }
      else {
        "var $javaName: ${fieldType.javaType}? = null"
      }
    }
    else -> error("Unsupported field type: $this")
  }
}

val ObjProperty<*, *>.implWsDataFieldInitializedCode: String
  get() = when (valueType) {
    is ValueType.Int, is ValueType.Boolean -> ""
    is ValueType.String, is ValueType.JvmClass, is ValueType.Collection<*, *>, is ValueType.Map<*, *> -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      "fun is${capitalizedFieldName}Initialized(): Boolean = ::${javaName}.isInitialized"
    }
    else -> error("Unsupported field type: $this")
  }
