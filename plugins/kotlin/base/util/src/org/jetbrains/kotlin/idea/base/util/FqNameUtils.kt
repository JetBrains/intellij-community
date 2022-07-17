// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FqNameUtils")
package org.jetbrains.kotlin.idea.base.util

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded

fun FqName.quoteIfNeeded(): FqName {
    return FqName(pathSegments().joinToString(".") { it.asString().quoteIfNeeded() })
}