// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrElse { 0 }
    println(result)
}
