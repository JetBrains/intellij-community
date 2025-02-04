// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

fun Char.renderAsEscapeSequence(): String =
    when (this) {
        '\r' -> """\r"""
        '\n' -> """\n"""
        '\t' -> """\t"""
        '\b' -> """\b"""
        '\'' -> """\'"""
        '\\' -> """\\"""
        else -> when (category.code.first()) {
            'C' /* control/format/surrogate/unassigned */,
            'Z' /* whitespace characters */,
            'M' /* combining marks */
                -> String.format("\\u%04x", code)
            else -> toString()
        }
    }

