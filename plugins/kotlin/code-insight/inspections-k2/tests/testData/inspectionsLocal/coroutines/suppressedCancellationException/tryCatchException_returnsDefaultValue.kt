// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

suspend fun compute(): Int {
    return try {
        delay(100)
        42
    } catch (<caret>e: Exception) {
        0
    }
}
