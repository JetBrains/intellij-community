// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

fun handleFailure(t: Throwable) {
    throw t
}

suspend fun compute(): Int? {
    return <caret>runCatching {
        delay(100)
        42
    }.onFailure { handleFailure(it) }.getOrNull()
}
