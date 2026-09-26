// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

// For a conditional 'ensureActive', we assume the user is aware of the potentially swallowed exception.
suspend fun compute(strict: Boolean): Int? {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrNull()
    if (strict) {
        currentCoroutineContext().ensureActive()
    }
    return result
}
