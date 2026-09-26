// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    val result = <caret>runCatching {
        delay(100)
        42
    }.onFailure { println(it) }
    println(result.getOrThrow())
}
