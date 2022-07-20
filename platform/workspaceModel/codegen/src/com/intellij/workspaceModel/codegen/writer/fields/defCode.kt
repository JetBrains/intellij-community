package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.deft.*

val ValueType<*>.kotlinType: String
  get() = javaType

val ValueType<*>.javaBuilderType: String
  get() = javaMutableType

val ValueType<*>.javaType: String
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

val ValueType<*>.javaMutableType: String
  get() = when (this) {
    TBoolean -> "Boolean"
    TInt -> "Int"
    TString -> "String"
    is TList<*> -> "MutableList<${elementType.javaType}>"
    is TSet<*> -> "MutableSet<${elementType.javaType}>"
    is TMap<*, *> -> "MutableMap<${keyType.javaType}, ${valueType.javaType}>"
    is TRef -> targetObjType.javaFullName
    is TStructure<*, *> -> box.javaFullName
    is TOptional<*> -> type.javaMutableType + "?"
    is TBlob<*> -> javaSimpleName
    else -> error("Unsupported type")
  }

fun ValueType<*>.unsupportedTypeError(): Nothing = error("Unsupported field type: $this")