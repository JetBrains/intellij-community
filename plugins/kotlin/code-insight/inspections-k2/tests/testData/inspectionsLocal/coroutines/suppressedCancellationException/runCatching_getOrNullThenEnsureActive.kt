// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

// The swallowed cancellation is re-raised right after the call
suspend fun compute(): Int? {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrNull()
    currentCoroutineContext().ensureActive()
    return result
}
