// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute() {
    <caret>runCatching {
        delay(100)
    }.onFailure {
        if (it is CancellationException) throw it
        println("failed")
    }
}
