// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
fun <T> seq(block: SeqScope<T>.() -> Unit): Sequence<T> = Sequence {
    val secScope = DefaultSeqScope<T>()
    block(secScope)
    val lazySequence = secScope.values
    SeqIterator(lazySequence)
}


private class DefaultSeqScope<T> : SeqScope<T>() {
    val values: MutableList<Iterable<T>> = mutableListOf()
    override fun yield(value: () -> T?) {
        values.add(object : Iterable<T> {
            override fun iterator(): Iterator<T> = object : TransformingIterator<T>() {
                private var provider: (() -> T?)? = value
                override fun calculateHasNext(): Boolean = provider != null

                override fun calculateNext(): T? {
                    val p = provider ?: throw NoSuchElementException()
                    provider = null
                    return p()
                }
            }
        })
    }

    override fun yieldAll(value: Iterable<T>) {
        values.add(value)
    }
}

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

private class SeqIterator<T>(providers: List<Iterable<T?>>) : TransformingIterator<T>() {
    private var providersIterator: Iterator<Iterable<T?>>? = providers.iterator()
    private var iterator: Iterator<T?>? = null
    override fun calculateHasNext(): Boolean {
        while (providersIterator != null) {
            if (iterator?.hasNext() == true) return true
            val providersIt = providersIterator
            if (providersIt?.hasNext() != true) {
                return false
            }
            val next = providersIt.next()
            val nextIterator = next.iterator()
            iterator = nextIterator
            if (nextIterator.hasNext()) return true
        }
        return false
    }

    override fun calculateNext(): T? = iterator?.next()

    override fun hasNext(): Boolean {
        val hasNext = super.hasNext()
        if (!hasNext) {
            iterator = null
            providersIterator = null
        }
        return hasNext
    }
}

@ApiStatus.Internal
abstract class SeqScope<in T> internal constructor() {
    abstract fun yield(value: () -> T?)

    abstract fun yieldAll(value: Iterable<T>)

}
