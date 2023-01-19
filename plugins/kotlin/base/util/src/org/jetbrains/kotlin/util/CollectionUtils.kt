// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.cast

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
 * Returns a [List] containing the first elements satisfying [predicate], as well as the subsequent first element for which [predicate] is
 * not satisfied (if such an element exists).
 */
fun <T> List<T>.takeWhileInclusive(predicate: (T) -> Boolean): List<T> {
    val inclusiveIndex = indexOfFirst { !predicate(it) }
    if (inclusiveIndex == -1) {
        // Needs to return a defensive copy because `takeWhile` is expected to return a new list.
        return toList()
    }
    return slice(0..inclusiveIndex)
}

/**
 * Sorted by [selector] or preserves the order for elements where [selector] returns the same result
 */
@ApiStatus.Internal
fun <T, R : Comparable<R>> Sequence<T>.sortedConservativelyBy(selector: (T) -> R?): Sequence<T> =
    withIndex()
        .sortedWith(compareBy({ (_, value) -> selector(value) }, IndexedValue<T>::index))
        .map(IndexedValue<T>::value)

private fun <T> Sequence<T>.cycle(): Sequence<T> = sequence { while (true) yieldAll(this@cycle) }

/**
 * Matches types of the first N elements of the [Sequence].
 *
 * @return `null` if one of the types didn't match; otherwise, returns the last matched element.
 */
@ApiStatus.Internal
fun <T : Any> Sequence<Any>.match(vararg expectedTypes: KClass<*>, last: KClass<T>): T? =
    (expectedTypes.asSequence() + last).zip(this + sequenceOf(null).cycle())
        .map { (expectedType, parent) -> parent?.takeIf(expectedType::isInstance) }
        .takeWhileInclusive { it != null }
        .lastOrNull()
        ?.let(last::cast)
