// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.ClassId

internal object ObjTypeConverter {
  private val kotlinDefaultTypeToValueType = mapOf(
    DefaultTypeClassIds.BOOLEAN to ValueType.Boolean,
    DefaultTypeClassIds.BYTE to ValueType.Byte,
    DefaultTypeClassIds.SHORT to ValueType.Short,
    DefaultTypeClassIds.INT to ValueType.Int,
    DefaultTypeClassIds.LONG to ValueType.Long,
    DefaultTypeClassIds.FLOAT to ValueType.Float,
    DefaultTypeClassIds.DOUBLE to ValueType.Double,
    DefaultTypeClassIds.CHAR to ValueType.Char,
    DefaultTypeClassIds.STRING to ValueType.String,
    StandardNames.FqNames.uByte to ValueType.UByte,
    StandardNames.FqNames.uShort to ValueType.UShort,
    StandardNames.FqNames.uInt to ValueType.UInt,
    StandardNames.FqNames.uLong to ValueType.ULong,
  )

  operator fun get(classId: ClassId?): ValueType.Primitive<*>? = classId?.let { kotlinDefaultTypeToValueType[it] }
}
