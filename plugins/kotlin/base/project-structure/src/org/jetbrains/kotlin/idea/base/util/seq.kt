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
    val values: MutableList<() -> T?> = mutableListOf()
    override fun yield(value: () -> T?) {
        values.add(value)
    }
}

abstract class TransformingIterator<T> : Iterator<T> {
    protected var next: T? = null

    protected abstract fun internalHasNext(): Boolean
    protected abstract fun internalNext(): T?

    override fun hasNext(): Boolean {
        while (internalHasNext()) {
            val t = internalNext()
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

private class SeqIterator<T>(providers: List<() -> T?>) : TransformingIterator<T>() {
    private var iterator: Iterator<() -> T?>? = providers.iterator()
    override fun internalHasNext(): Boolean {
        return iterator?.hasNext() == true
    }

    override fun internalNext(): T? = iterator?.next()?.invoke()

    override fun hasNext(): Boolean {
        val hasNext = super.hasNext()
        if (!hasNext) {
            iterator = null
        }
        return hasNext
    }
}

@ApiStatus.Internal
abstract class SeqScope<in T> internal constructor() {
    abstract fun yield(value: () -> T?)

}
