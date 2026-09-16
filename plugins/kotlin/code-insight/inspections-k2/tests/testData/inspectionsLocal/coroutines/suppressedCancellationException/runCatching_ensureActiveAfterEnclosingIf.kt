// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

// The 'ensureActive' is in the enclosing block rather than the innermost one
suspend fun compute(flag: Boolean): Int? {
    var result: Int? = null
    if (flag) {
        result = <caret>runCatching {
            delay(100)
            42
        }.getOrNull()
    }
    currentCoroutineContext().ensureActive()
    return result
}
