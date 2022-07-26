package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.deft.ValueType as OldValueType

val OldValueType<*>.javaType: String
  get() = when (this) {
    TBoolean -> "Boolean"
    TInt -> "Int"
    TString -> "String"
    is TList<*> -> "List<${elementType.javaType}>"
    is TSet<*> -> "Set<${elementType.javaType}>"
    is TMap<*, *> -> "Map<${keyType.javaType}, ${valueType.javaType}>"
    is TRef -> targetObjType.javaFullName
    is TStructure<*, *> -> box.javaFullName
    is TOptional<*> -> type.javaType + "?"
    is TBlob<*> -> javaSimpleName
    else -> error("Unsupported type")
  }

val ValueType<*>.javaType: String
  get() = when (this) {
    ValueType.Boolean -> "Boolean"
    ValueType.Int -> "Int"
    ValueType.String -> "String"
    is ValueType.List<*> -> "List<${elementType.javaType}>"
    is ValueType.Set<*> -> "Set<${elementType.javaType}>"
    is ValueType.Map<*, *> -> "Map<${keyType.javaType}, ${valueType.javaType}>"
    is ValueType.ObjRef -> target.javaFullName
    is ValueType.Optional<*> -> type.javaType + "?"
    is ValueType.JvmClass -> javaClassName
    else -> throw UnsupportedOperationException("$this type isn't supported")
  }

val ValueType<*>.javaMutableType: String
  get() = when (this) {
    is ValueType.List<*> -> "MutableList<${elementType.javaType}>"
    is ValueType.Set<*> -> "MutableSet<${elementType.javaType}>"
    is ValueType.Map<*, *> -> "MutableMap<${keyType.javaType}, ${valueType.javaType}>"
    is ValueType.Optional<*> -> type.javaMutableType + "?"
    else -> javaType
  }


fun ValueType<*>.unsupportedTypeError(): Nothing = error("Unsupported field type: $this")