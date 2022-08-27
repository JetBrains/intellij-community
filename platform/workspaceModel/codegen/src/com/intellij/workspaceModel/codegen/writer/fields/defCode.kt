package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.utils.QualifiedName
import com.intellij.workspaceModel.codegen.utils.toQualifiedName

val ValueType<*>.javaType: QualifiedName
  get() = when (this) {
    ValueType.Boolean -> "Boolean".toQualifiedName()
    ValueType.Int -> "Int".toQualifiedName()
    ValueType.String -> "String".toQualifiedName()
    is ValueType.List<*> -> "List".toQualifiedName().appendSuffix("<${elementType.javaType}>")
    is ValueType.Set<*> -> "Set".toQualifiedName().appendSuffix("<${elementType.javaType}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName().appendSuffix("<${keyType.javaType}, ${valueType.javaType}>")
    is ValueType.ObjRef -> target.javaFullName
    is ValueType.Optional<*> -> type.javaType.appendSuffix("?")
    is ValueType.JvmClass -> javaClassName.toQualifiedName()
    else -> throw UnsupportedOperationException("$this type isn't supported")
  }

val ValueType<*>.javaMutableType: QualifiedName
  get() = when (this) {
    is ValueType.List<*> -> "MutableList".toQualifiedName().appendSuffix("<${elementType.javaType}>")
    is ValueType.Set<*> -> "MutableSet".toQualifiedName().appendSuffix("<${elementType.javaType}>")
    is ValueType.Map<*, *> -> "MutableMap".toQualifiedName().appendSuffix("<${keyType.javaType}, ${valueType.javaType}>")
    is ValueType.Optional<*> -> type.javaMutableType.appendSuffix("?")
    else -> javaType
  }


fun ValueType<*>.unsupportedTypeError(): Nothing = error("Unsupported field type: $this")