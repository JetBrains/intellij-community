// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

fun parse(): Int = 42

// Function references cannot suspend
suspend fun compute(): Int? {
    delay(100)
    return <caret>runCatching(::parse).getOrNull()
}
