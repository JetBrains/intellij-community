// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(): Int {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrNull()
    if (result == null) throw IllegalStateException("no result")
    return result
}
