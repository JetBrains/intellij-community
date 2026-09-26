// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

// Logging is not handling: the exception goes nowhere else
fun logError(message: String, t: Throwable) {
    println("$message: $t")
}

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        logError("failed", e)
    }
}
