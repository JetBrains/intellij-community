// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import kotlinx.coroutines.delay

// The inline lambda still runs in the suspend context of 'compute'
suspend fun compute(items: List<Int>) {
    items.forEach {
        try {
            delay(it.toLong())
        } catch (<caret>e: Exception) {
        }
    }
}
