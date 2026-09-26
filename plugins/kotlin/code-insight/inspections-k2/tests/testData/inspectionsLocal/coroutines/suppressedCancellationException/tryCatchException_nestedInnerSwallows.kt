// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    try {
        try {
            delay(100)
        } catch (<caret>e: Exception) {
        }
    } catch (e: Exception) {
        throw e
    }
}
