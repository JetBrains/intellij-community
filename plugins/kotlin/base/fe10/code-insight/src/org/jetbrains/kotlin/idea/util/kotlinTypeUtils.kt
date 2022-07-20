// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.types.KotlinType

fun KotlinType.getDefaultInitializer(): String? =
    when {
      isMarkedNullable -> "null"
      KotlinBuiltIns.isFloat(this) -> "0.0f"
      KotlinBuiltIns.isDouble(this) -> "0.0"
      KotlinBuiltIns.isChar(this) -> "'\\u0000'"
      KotlinBuiltIns.isBoolean(this) -> "false"
      KotlinBuiltIns.isUnit(this) -> "Unit"
      KotlinBuiltIns.isString(this) -> "\"\""
      KotlinBuiltIns.isInt(this) ||
      KotlinBuiltIns.isLong(this) ||
      KotlinBuiltIns.isShort(this) ||
      KotlinBuiltIns.isByte(this) -> "0"
      else -> null
    }
