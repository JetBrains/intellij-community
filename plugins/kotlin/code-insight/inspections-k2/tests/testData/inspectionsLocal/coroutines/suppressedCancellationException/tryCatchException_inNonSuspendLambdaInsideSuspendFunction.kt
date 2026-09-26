// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

fun runLater(block: () -> Unit) {
    block()
}

// The lambda is not inline and not suspend: its body runs outside of the coroutine
suspend fun compute() {
    delay(100)
    runLater {
        try {
            println("work")
        } catch (<caret>e: Exception) {
        }
    }
}
