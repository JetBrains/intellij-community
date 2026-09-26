// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

fun logAndThrow(t: Throwable): Nothing = throw t

// The name contains 'throw', so this is not treated as logging-only
suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        logAndThrow(e)
    }
}
