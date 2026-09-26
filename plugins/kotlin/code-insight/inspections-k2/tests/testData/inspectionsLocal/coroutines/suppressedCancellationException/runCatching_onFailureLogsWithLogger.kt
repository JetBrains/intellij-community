// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.delay

fun logWarning(message: String, t: Throwable) {
    println("$message: $t")
}

// Logging the failure does not propagate it
suspend fun compute(): Int? {
    return <caret>runCatching {
        delay(100)
        42
    }.onFailure { logWarning("failed", it) }.getOrNull()
}
