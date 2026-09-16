// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.delay

suspend fun compute(): Int? {
    return (<caret>runCatching {
        delay(100)
        42
    }).map {
        if (it > 0) throw IllegalStateException("unexpected")
        it
    }.getOrNull()
}
