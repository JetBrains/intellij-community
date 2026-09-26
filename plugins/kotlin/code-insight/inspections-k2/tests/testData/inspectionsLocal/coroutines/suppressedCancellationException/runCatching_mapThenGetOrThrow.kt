// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(): Int {
    return <caret>runCatching {
        delay(100)
        42
    }.map { it + 1 }.getOrThrow()
}
