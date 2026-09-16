// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// If the result is stored in a variable, we disable the inspection and do not track it further
suspend fun compute(): Int {
    val result = <caret>runCatching {
        delay(100)
        42
    }.map { it + 1 }
    return result.getOrThrow()
}
