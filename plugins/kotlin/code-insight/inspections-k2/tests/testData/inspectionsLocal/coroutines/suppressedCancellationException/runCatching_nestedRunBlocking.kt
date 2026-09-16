// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// `runBlocking` starts its own coroutine. The 'CancellationException' of the caller cannot reach `delay`.
suspend fun compute(): Int? {
    return <caret>runCatching {
        runBlocking {
            delay(100)
            42
        }
    }.getOrNull()
}
