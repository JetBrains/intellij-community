// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.utils

object AccessorUtils {
    fun canBePropertyAccessor(identifier: String): Boolean {
        return identifier.startsWith("get") || identifier.startsWith("is") || identifier.startsWith("set")
    }
}