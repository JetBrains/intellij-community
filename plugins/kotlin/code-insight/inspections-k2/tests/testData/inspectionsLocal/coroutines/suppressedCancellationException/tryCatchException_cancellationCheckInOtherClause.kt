// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation with 'ensureActive()'
package test

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute() {
    try {
        delay(100)
    } catch (e: IOException) {
        if (e.cause is CancellationException) println("cancelled")
    } catch (<caret>e: Exception) {
        println("failed")
    }
}
