// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

suspend fun compute(): Int {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        return -1
    }
    return 0
}
