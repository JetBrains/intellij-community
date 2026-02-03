// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("StringUtils")

package org.jetbrains.kotlin.idea.base.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun String.collapseSpaces(): String {
    val builder = StringBuilder()
    var haveSpaces = false
    for (c in this) {
        if (c.isWhitespace()) {
            haveSpaces = true
        } else {
            if (haveSpaces) {
                builder.append(" ")
                haveSpaces = false
            }
            builder.append(c)
        }
    }
    return builder.toString()
}

@ApiStatus.Internal
fun String.substringBeforeLastOrNull(delimiter: String): String? {
    val index = lastIndexOf(delimiter)
    return if (index == -1) null else substring(0, index)
}

@ApiStatus.Internal
fun String.substringAfterLastOrNull(delimiter: String): String? {
    val index = lastIndexOf(delimiter)
    return if (index == -1) null else substring(index + 1, length)
}