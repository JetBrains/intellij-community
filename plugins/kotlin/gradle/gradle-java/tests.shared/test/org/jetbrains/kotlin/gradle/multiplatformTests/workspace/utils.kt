// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

import org.jetbrains.kotlin.utils.Printer

internal fun <T> Collection<T>.joinToStringWithSorting(separator: String = ", ", toString: (T) -> String = { it.toString() }): String =
    map { toString(it) }.sorted().joinToString(separator)

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}