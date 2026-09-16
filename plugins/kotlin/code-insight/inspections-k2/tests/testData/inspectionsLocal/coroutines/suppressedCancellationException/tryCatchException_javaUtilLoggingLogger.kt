// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.delay

private val LOG = Logger.getLogger("test")

// Handing the exception to a real logger does not propagate it
suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        LOG.log(Level.WARNING, "failed", e)
    }
}
