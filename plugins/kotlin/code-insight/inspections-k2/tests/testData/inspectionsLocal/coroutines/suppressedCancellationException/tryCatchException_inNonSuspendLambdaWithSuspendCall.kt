// WITH_COROUTINES
// PROBLEM: none
// K2_ERROR: NON_LOCAL_SUSPENSION_POINT
package test

import kotlinx.coroutines.delay

fun runLater(block: () -> Unit) {
    block()
}

// The `delay` call is not valid here. The inspection must still not fail.
suspend fun compute() {
    try {
        runLater {
            delay(100)
        }
    } catch (<caret>e: Exception) {
    }
}
