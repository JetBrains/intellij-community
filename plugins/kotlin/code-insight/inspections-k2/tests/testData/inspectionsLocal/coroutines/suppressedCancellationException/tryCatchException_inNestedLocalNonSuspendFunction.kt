// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute() {
    fun parse(raw: String): Int = try {
        raw.toInt()
    } catch (<caret>e: Exception) {
        0
    }

    println(parse("1"))
    delay(100)
}
