// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        println("failed")
    }
    currentCoroutineContext().ensureActive()
}
