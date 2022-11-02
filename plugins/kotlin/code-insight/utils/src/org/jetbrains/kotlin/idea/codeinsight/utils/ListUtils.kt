// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

/**
 * Returns a list containing the first elements satisfying [predicate], as well as the first element for which [predicate] is not satisfied.
 */
fun <T> List<T>.takeWhileInclusive(predicate: (T) -> Boolean): List<T> {
    val inclusiveIndex = indexOfFirst { !predicate(it) }
    if (inclusiveIndex == -1) {
        // Needs to return a defensive copy because `takeWhile` is expected to return a new list.
        return toList()
    }
    return slice(0..inclusiveIndex)
}