// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

fun <T> Collection<T>.joinToStringWithSorting(separator: String = ", ", toString: (T) -> String = { it.toString() }): String =
    map { toString(it) }.sorted().joinToString(separator)
