// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

suspend fun compute(retry: Boolean) {
    try {
        delay(100)
    } catch (<caret>e: Exception) {
        when {
            retry -> println("retrying")
            else -> throw e
        }
    }
}
