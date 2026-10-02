// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

typealias Cancelled = CancellationException

suspend fun compute(): Boolean {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        if (e is Cancelled) return false
        println("failed")
    }
    return true
}
