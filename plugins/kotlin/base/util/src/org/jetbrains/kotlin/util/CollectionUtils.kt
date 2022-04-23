// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import java.util.*

inline fun <T, R> Collection<T>.mapAll(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>(this.size)
    for (item in this) {
        result += transform(item) ?: return null
    }
    return result
}

fun <K, V> merge(vararg maps: Map<K, V>?): Map<K, V> {
    val result = mutableMapOf<K, V>()
    for (map in maps) {
        if (map != null) {
            result.putAll(map)
        }
    }
    return result
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> Sequence<*>.takeWhileIsInstance(): Sequence<T> = takeWhile { it is T } as Sequence<T>
