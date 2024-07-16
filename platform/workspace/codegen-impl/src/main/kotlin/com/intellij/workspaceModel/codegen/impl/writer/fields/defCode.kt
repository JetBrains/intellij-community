// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.QualifiedName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaBuilderFqnName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.toQualifiedName

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
    is ValueType.JvmClass -> kotlinClassName.toQualifiedName()
    else -> throw UnsupportedOperationException("$this type isn't supported")
  }

/**
 * Generate builder code with the generics like `<out Entity>` if they needed
 */
val ValueType<*>.javaBuilderTypeWithGeneric: QualifiedName
  get() = when (this) {
    ValueType.Boolean -> "Boolean".toQualifiedName()
    ValueType.Int -> "Int".toQualifiedName()
    ValueType.String -> "String".toQualifiedName()
    is ValueType.List<*> -> "List".toQualifiedName().appendSuffix("<${elementType.javaBuilderTypeWithGeneric}>")
    is ValueType.Set<*> -> "Set".toQualifiedName().appendSuffix("<${elementType.javaBuilderTypeWithGeneric}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName().appendSuffix("<${keyType.javaBuilderTypeWithGeneric}, ${valueType.javaBuilderTypeWithGeneric}>")
    is ValueType.ObjRef -> {
      val out = if (target.openness == ObjClass.Openness.abstract) "out " else ""
      target.javaBuilderFqnName.appendSuffix(if (target.builderWithTypeParameter) "<$out${this.javaType}>" else "")
    }
    is ValueType.Optional<*> -> type.javaBuilderTypeWithGeneric.appendSuffix("?")
    is ValueType.JvmClass -> kotlinClassName.toQualifiedName()
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


val ValueType<*>.entityType: QualifiedName
  get() = when (this) {
    ValueType.Boolean -> "Boolean".toQualifiedName()
    ValueType.Int -> "Int".toQualifiedName()
    ValueType.String -> "String".toQualifiedName()
    is ValueType.List<*> -> elementType.entityType
    is ValueType.Set<*> -> elementType.entityType
    is ValueType.Map<*, *> -> throw UnsupportedOperationException("$this type isn't supported")
    is ValueType.ObjRef -> target.javaFullName
    is ValueType.Optional<*> -> type.entityType
    is ValueType.JvmClass -> kotlinClassName.toQualifiedName()
    else -> throw UnsupportedOperationException("$this type isn't supported")
  }

fun ValueType<*>.unsupportedTypeError(): Nothing = error("Unsupported field type: $this")