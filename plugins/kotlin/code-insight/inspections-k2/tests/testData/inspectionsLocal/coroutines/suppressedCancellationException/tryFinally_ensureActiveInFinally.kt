// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

suspend fun compute(): Int? {
    var result: Int? = null
    try {
        result = <caret>runCatching {
            delay(100)
            42
        }.getOrNull()
    } finally {
        currentCoroutineContext().ensureActive()
    }
    return result
}
