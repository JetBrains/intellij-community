// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute() {
    try {
        delay(100)
    } catch (e: CancellationException) {
        throw e
    } catch (<caret>e: Exception) {
    }
}
