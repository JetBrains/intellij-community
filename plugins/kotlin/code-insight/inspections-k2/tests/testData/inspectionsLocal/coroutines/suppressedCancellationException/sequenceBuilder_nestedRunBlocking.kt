// WITH_COROUTINES
// PROBLEM: 'catch' clause suppresses 'CancellationException' and breaks coroutine cancellation
package test

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// `runBlocking` starts its own suspend context that is disconnected from the sequence builder.
// Here, we need to report any suppressions.
fun numbers(): Sequence<Int> = sequence {
    val value = runBlocking {
        try {
            delay(100)
            42
        } catch (<caret>e: Exception) {
            0
        }
    }
    yield(value)
}
