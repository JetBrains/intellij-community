// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(): Throwable? {
    return <caret>runCatching {
        delay(100)
        42
    }.exceptionOrNull()
}
