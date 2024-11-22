// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util


abstract class TransformingIterator<T> : Iterator<T> {
    protected var next: T? = null

    protected abstract fun calculateHasNext(): Boolean
    protected abstract fun calculateNext(): T?

    override fun hasNext(): Boolean {
        while (calculateHasNext()) {
            val t = calculateNext()
            if (t != null) {
                next = t
                return true
            }
        }
        next = null
        return false
    }

    override fun next(): T {
        val v = next
        next = null
        if (v != null) return v
        hasNext()
        val v1 = next
        next = null
        @Suppress("KotlinConstantConditions")
        return v1 ?: throw NoSuchElementException()
    }

}

class MappingIterator<K, T>(
    private val iterator: Iterator<K>,
    private val mapping: (K) -> T?
): TransformingIterator<T>() {
    override fun calculateHasNext(): Boolean = iterator.hasNext()

    override fun calculateNext(): T? = mapping(iterator.next())
}

