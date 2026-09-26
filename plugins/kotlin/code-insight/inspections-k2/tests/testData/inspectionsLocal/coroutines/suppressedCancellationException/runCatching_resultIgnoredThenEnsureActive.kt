// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

suspend fun compute() {
    <caret>runCatching {
        delay(100)
    }
    currentCoroutineContext().ensureActive()
}
