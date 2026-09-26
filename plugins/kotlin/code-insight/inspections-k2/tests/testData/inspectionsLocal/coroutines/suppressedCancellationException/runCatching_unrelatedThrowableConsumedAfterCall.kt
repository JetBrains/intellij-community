// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.delay

fun record(t: Throwable) {
    println(t)
}

suspend fun compute(previous: Throwable): Int? {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrNull()
    record(previous)
    return result
}
