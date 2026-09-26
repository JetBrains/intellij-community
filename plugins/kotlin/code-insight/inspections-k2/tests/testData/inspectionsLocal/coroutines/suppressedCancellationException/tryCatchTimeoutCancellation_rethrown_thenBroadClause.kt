// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

suspend fun compute(): Int {
    return try {
        withTimeout(1000) {
            delay(100)
            42
        }
    } catch (e: TimeoutCancellationException) {
        throw e
    } catch (<caret>e: Exception) {
        0
    }
}
