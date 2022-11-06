// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import org.jetbrains.annotations.ApiStatus
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

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Sequence<*>.takeWhileIsInstance(): Sequence<T> = takeWhile { it is T } as Sequence<T>

@ApiStatus.Internal
fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> =
    sequence {
        for (elem in this@takeWhileInclusive) {
            yield(elem)
            if (!predicate(elem)) break
        }
    }

/**
 * Sorted by [selector] or preserves the order for elements where [selector] returns the same result
 */
@ApiStatus.Internal
fun <T, R : Comparable<R>> Sequence<T>.sortedConservativelyBy(selector: (T) -> R?): Sequence<T> =
    withIndex()
        .sortedWith(compareBy({ (_, value) -> selector(value) }, IndexedValue<T>::index))
        .map(IndexedValue<T>::value)
