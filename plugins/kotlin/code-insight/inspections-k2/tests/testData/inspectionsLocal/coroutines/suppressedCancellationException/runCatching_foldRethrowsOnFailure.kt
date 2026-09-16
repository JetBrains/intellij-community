// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// The failure branch re-throws, so nothing is suppressed
suspend fun compute(): Int {
    return <caret>runCatching {
        delay(100)
        42
    }.fold(onSuccess = { it }, onFailure = { throw it })
}
