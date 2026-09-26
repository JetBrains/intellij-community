// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// The failure is rethrown, just not inside the 'runCatching' call chain
suspend fun compute() {
    val result = <caret>runCatching {
        delay(100)
    }
    val failure = result.exceptionOrNull()
    if (failure != null) throw failure
}
