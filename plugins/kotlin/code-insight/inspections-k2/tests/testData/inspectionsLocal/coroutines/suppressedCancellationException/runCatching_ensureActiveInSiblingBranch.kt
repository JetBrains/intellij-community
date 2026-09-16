// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

suspend fun compute(flag: Boolean): Int? {
    var result: Int? = null
    if (flag) {
        result = <caret>runCatching {
            delay(100)
            42
        }.getOrNull()
    } else {
        currentCoroutineContext().ensureActive()
    }
    return result
}
