// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(raw: String): Int {
    delay(100)
    return try {
        raw.toInt()
    } catch (<caret>e: NumberFormatException) {
        0
    }
}
