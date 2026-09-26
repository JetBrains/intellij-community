// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

suspend fun compute(): String {
    var result = "ok"
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        result = "failed"
    }
    return result
}
