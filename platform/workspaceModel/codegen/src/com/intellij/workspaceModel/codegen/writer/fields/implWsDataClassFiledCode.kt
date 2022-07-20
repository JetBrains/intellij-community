package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.isOverride
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.javaName

val Field<*, *>.implWsDataFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) append(implWsBlockingCodeOverride)
      else append(implWsDataBlockingCode)
    }
    else {
      if (defaultValue!!.startsWith("=")) {
        append("var $javaName: ${type.javaType} ${defaultValue}")
      } else {
        append("var $javaName: ${type.javaType} = ${defaultValue}")
      }
    }
  }
private val Field<*, *>.implWsDataBlockingCode: String
  get() = implWsDataBlockCode(type, name)

private fun Field<*, *>.implWsDataBlockCode(fieldType: ValueType<*>, name: String, isOptional: Boolean = false): String {
  return when (fieldType) {
    TInt -> "var $javaName: ${fieldType.javaType} = 0"
    TBoolean -> "var $javaName: ${fieldType.javaType} = false"
    TString -> "lateinit var $javaName: String"
    is TRef -> error("Reference type at EntityData not supported")
    is TCollection<*, *>, is TMap<*, *> -> {
      if (fieldType.isRefType()) error("Reference type at EntityData not supported")
      if (!isOptional) {
        "lateinit var $javaName: ${fieldType.javaType}"
      }
      else {
        "var $javaName: ${fieldType.javaType}? = null"
      }
    }
    is TOptional<*> -> when (fieldType.type) {
      TInt, TBoolean, TString -> "var $javaName: ${fieldType.javaType} = null"
      else -> implWsDataBlockCode(fieldType.type, name, true)
    }
    is TBlob<*> -> {
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

val Field<*, *>.implWsDataFieldInitializedCode: String
  get() = when (type) {
    is TInt, is TBoolean -> ""
    is TString, is TBlob<*>, is TCollection<*, *>, is TMap<*, *> -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      "fun is${capitalizedFieldName}Initialized(): Boolean = ::${javaName}.isInitialized"
    }
    else -> error("Unsupported field type: $this")
  }
