// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// Resolves to the declaration below, not to 'kotlin.runCatching'
suspend fun <T> runCatching(block: suspend () -> T): T? = block()

suspend fun compute(): Int? {
    return <caret>runCatching {
        delay(100)
        42
    }
}
