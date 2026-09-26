// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        if (e is CancellationException) throw e
        println("failed")
    }
}
