// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute(): Int? {
    return <caret>runCatching {
        delay(100)
        42
    }.recover {
        if (it is CancellationException) throw it
        0
    }.getOrNull()
}
