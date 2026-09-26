package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.impl.metadata.model.getPrimitiveTypeConstructor

data class PrimitiveMetadata(private val type: String, private val isNullable: Boolean) {
  fun getVariableName(): String {
    val nullableOrNot = if (isNullable) "Nullable" else "NotNullable"
    return "primitiveType$type$nullableOrNot"
  }

  fun getConstructor(): String = getPrimitiveTypeConstructor(isNullable, type.escapeDollar().withDoubleQuotes())
}