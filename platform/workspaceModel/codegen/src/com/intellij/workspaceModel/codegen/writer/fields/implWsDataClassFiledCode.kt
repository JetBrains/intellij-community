package org.jetbrains.deft.codegen.ijws.fields

import deft.storage.codegen.*
import deft.storage.codegen.field.implSuspendableCode
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implWsDataFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) append(implWsBlockingCodeOverride)
      else append(implWsDataBlockingCode)

      if (suspendable == true) append("\n").append(implSuspendableCode)
    }
    else {
      append("var $javaName: ${type.javaType} ${defaultValue}")
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
    is TList<*>, is TMap<*, *> -> {
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
    is TString, is TBlob<*>, is TList<*>, is TMap<*, *> -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      "fun is${capitalizedFieldName}Initialized(): Boolean = ::${javaName}.isInitialized"
    }
    else -> error("Unsupported field type: $this")
  }
