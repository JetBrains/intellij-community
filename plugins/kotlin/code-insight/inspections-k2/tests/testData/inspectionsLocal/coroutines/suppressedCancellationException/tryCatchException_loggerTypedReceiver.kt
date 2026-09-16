// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

class AppLogger {
    fun error(message: String, t: Throwable) {
        println("$message: $t")
    }
}

private val LOG = AppLogger()

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        LOG.error("failed", e)
    }
}
