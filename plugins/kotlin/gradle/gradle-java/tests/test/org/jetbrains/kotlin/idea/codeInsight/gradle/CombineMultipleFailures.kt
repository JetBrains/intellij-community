// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.junit.runners.model.MultipleFailureException

fun combineMultipleFailures(action: CombineMultipleFailuresContext.() -> Unit) {
    val context = CombineMultipleFailuresContext()
    try {
        context.action()
    } finally {
        context.throwExceptionsIfAny()
    }
}

fun <T> Iterable<T>.combineMultipleFailures(assertion: (T) -> Unit) {
    org.jetbrains.kotlin.idea.codeInsight.gradle.combineMultipleFailures {
        forEach { value -> runAssertion { assertion(value) } }
    }
}

class CombineMultipleFailuresContext {
    private val failures = mutableListOf<Throwable>()

    fun runAssertion(assertion: () -> Unit) {
        failures += when (val exception = runCatching { assertion() }.exceptionOrNull()) {
            null -> return
            is MultipleFailureException -> exception.failures
            else -> listOf(exception)
        }
    }

    fun throwExceptionsIfAny(): Nothing? {
        if (failures.isEmpty()) return null
        if (failures.size == 1) throw failures.single()
        throw MultipleFailureException(failures)
    }
}
