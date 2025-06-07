// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.metaModel

import com.intellij.psi.CommonClassNames
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import org.jetbrains.kotlin.name.FqName

internal object ObjTypeConverter {
  private val javaPrimitiveTypes = mapOf(
    CommonClassNames.JAVA_LANG_BOOLEAN to ValueType.Boolean,
    CommonClassNames.JAVA_LANG_BYTE to ValueType.Byte,
    CommonClassNames.JAVA_LANG_SHORT to ValueType.Short,
    CommonClassNames.JAVA_LANG_INTEGER to ValueType.Int,
    CommonClassNames.JAVA_LANG_LONG to ValueType.Long,
    CommonClassNames.JAVA_LANG_FLOAT to ValueType.Float,
    CommonClassNames.JAVA_LANG_DOUBLE to ValueType.Double,
    CommonClassNames.JAVA_LANG_CHARACTER to ValueType.Char,
    CommonClassNames.JAVA_LANG_STRING to ValueType.String,
  )

  private val kotlinPrimitiveTypes = mapOf(
    "kotlin.Boolean" to ValueType.Boolean,
    "kotlin.Byte" to ValueType.Byte,
    "kotlin.Short" to ValueType.Short,
    "kotlin.Int" to ValueType.Int,
    "kotlin.Long" to ValueType.Long,
    "kotlin.Float" to ValueType.Float,
    "kotlin.Double" to ValueType.Double,
    "kotlin.UByte" to ValueType.UByte,
    "kotlin.UShort" to ValueType.UShort,
    "kotlin.UInt" to ValueType.UInt,
    "kotlin.ULong" to ValueType.ULong,
    "kotlin.Char" to ValueType.Char,
    "kotlin.String" to ValueType.String,
  )

  operator fun get(fqName: FqName): ValueType.Primitive<*>? =
    kotlinPrimitiveTypes[fqName.asString()] ?: javaPrimitiveTypes[fqName.asString()]
}
