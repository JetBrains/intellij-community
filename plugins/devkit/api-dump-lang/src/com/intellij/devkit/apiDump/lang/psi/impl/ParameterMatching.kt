// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElement
import com.intellij.lang.jvm.JvmParameter
import com.intellij.lang.jvm.JvmTypeParameter
import com.intellij.lang.jvm.types.JvmArrayType
import com.intellij.lang.jvm.types.JvmPrimitiveType
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.lang.jvm.types.JvmType
import com.intellij.lang.jvm.types.JvmTypeVisitor
import com.intellij.lang.jvm.types.JvmWildcardType

internal fun parametersMatch(jvmParameters: Array<JvmParameter>, parameters: List<String>): Boolean {
  val methodParameters = jvmParameters
  if (methodParameters.size != parameters.size) return false

  return methodParameters.zip(parameters).all { (parameter, text) ->
    parameter.type.asText() == text
  }
}

private fun JvmType.asText(): String =
  accept(TypeTextBuilder)

private fun JvmElement.asText(): String =
  accept(FQNBuilder)

private object TypeTextBuilder: JvmTypeVisitor<String> {
  override fun visitReferenceType(type: JvmReferenceType): String =
    type.resolve()?.asText() ?: ""

  override fun visitPrimitiveType(type: JvmPrimitiveType): String =
    when (type.kind) {
      JvmPrimitiveTypeKind.INT -> "I"
      JvmPrimitiveTypeKind.LONG -> "J"
      JvmPrimitiveTypeKind.FLOAT -> "F"
      JvmPrimitiveTypeKind.DOUBLE -> "D"
      JvmPrimitiveTypeKind.BYTE -> "B"
      JvmPrimitiveTypeKind.CHAR -> "C"
      JvmPrimitiveTypeKind.SHORT -> "S"
      JvmPrimitiveTypeKind.BOOLEAN -> "Z"
      JvmPrimitiveTypeKind.VOID -> "V"
      else -> throw UnsupportedOperationException(type.kind.toString())
    }

  override fun visitArrayType(type: JvmArrayType): String? =
    type.componentType.accept(this) + "[]"

  override fun visitWildcardType(type: JvmWildcardType): String? =
    type.lowerBound().accept(this)

  override fun visitType(type: JvmType): String? =
    throw UnsupportedOperationException(type.toString())
}

private object FQNBuilder : DefaultJvmElementVisitor<String> {
  override fun visitClass(clazz: JvmClass): String? =
    clazz.qualifiedName

  override fun visitElement(element: JvmElement): String? =
    throw UnsupportedOperationException(element.toString())

  override fun visitTypeParameter(typeParameter: JvmTypeParameter): String? {
    val bounds = typeParameter.bounds
    return when (bounds.size) {
      0 -> "java.lang.Object"
      1 -> bounds.single().asText()
      else -> "java.lang.Object" // todo
    }
  }
}
