// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

fun reportFailure(message: String) {
    println(message)
}

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        reportFailure("failed")
    }
}
