// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.ClassId

internal object ObjTypeConverter {
  private val kotlinDefaultTypeToValueType = mapOf(
    KaStandardTypeClassIds.BOOLEAN to ValueType.Boolean,
    KaStandardTypeClassIds.BYTE to ValueType.Byte,
    KaStandardTypeClassIds.SHORT to ValueType.Short,
    KaStandardTypeClassIds.INT to ValueType.Int,
    KaStandardTypeClassIds.LONG to ValueType.Long,
    KaStandardTypeClassIds.FLOAT to ValueType.Float,
    KaStandardTypeClassIds.DOUBLE to ValueType.Double,
    KaStandardTypeClassIds.CHAR to ValueType.Char,
    KaStandardTypeClassIds.STRING to ValueType.String,
    StandardNames.FqNames.uByte to ValueType.UByte,
    StandardNames.FqNames.uShort to ValueType.UShort,
    StandardNames.FqNames.uInt to ValueType.UInt,
    StandardNames.FqNames.uLong to ValueType.ULong,
  )

  operator fun get(classId: ClassId?): ValueType.Primitive<*>? = classId?.let { kotlinDefaultTypeToValueType[it] }
}
