// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// 'handle' receives the exception and might rethrow it
fun handle(t: Throwable) {
    throw t
}

suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        handle(e)
    }
}
