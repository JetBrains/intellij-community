package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.StorageCollection
import com.intellij.workspaceModel.codegen.impl.writer.extensions.hasSetter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isOverride
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.generatedCodeVisibilityModifier

internal val ObjProperty<*, *>.implWsDataFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) append(implWsBlockingCodeOverride)
      else append("$generatedCodeVisibilityModifier$implWsDataBlockingCode")
    }
    else {
      val expression = when (val kind = valueKind) {
        is ObjProperty.ValueKind.Computable -> kind.expression
        is ObjProperty.ValueKind.WithDefault -> kind.value
        else -> error(kind)
      }
      val changeToMutable = valueType is ValueType.Collection<*, *> && !valueType.isRefType()
      val javaType = if (changeToMutable) valueType.javaMutableType else valueType.javaType
      val toMutable = when {
        changeToMutable && valueType is ValueType.List<*> -> ".${StorageCollection.toMutableWorkspaceList}()"
        changeToMutable && valueType is ValueType.Set<*> -> ".${StorageCollection.toMutableWorkspaceSet}()"
        else -> ""
      }
      if (expression.startsWith("=")) {
        append("${generatedCodeVisibilityModifier}var $javaName: $javaType $expression$toMutable")
      }
      else {
        append("${generatedCodeVisibilityModifier}var $javaName: $javaType = $expression$toMutable")
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
    ValueType.Char -> "var $javaName: ${fieldType.javaType} = 0.toChar()"
    ValueType.Long -> "var $javaName: ${fieldType.javaType} = 0"
    ValueType.Float -> "var $javaName: ${fieldType.javaType} = 0f"
    ValueType.Double -> "var $javaName: ${fieldType.javaType} = 0.0"
    ValueType.Short -> "var $javaName: ${fieldType.javaType} = 0"
    ValueType.Byte -> "var $javaName: ${fieldType.javaType} = 0"
    ValueType.UByte -> "var $javaName: ${fieldType.javaType} = 0u"
    ValueType.UShort -> "var $javaName: ${fieldType.javaType} = 0u"
    ValueType.UInt -> "var $javaName: ${fieldType.javaType} = 0u"
    ValueType.ULong -> "var $javaName: ${fieldType.javaType} = 0u"
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
      ValueType.String, ValueType.Int, ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
      ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> "var $javaName: ${fieldType.javaType} = null"
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
    ValueType.Int, ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> ""
    is ValueType.String, is ValueType.JvmClass, is ValueType.Collection<*, *>, is ValueType.Map<*, *> -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      "internal fun is${capitalizedFieldName}Initialized(): Boolean = ::${javaName}.isInitialized"
    }
    else -> error("Unsupported field type: $this")
  }
