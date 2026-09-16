// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay
import java.io.IOException

// 'IOException' can never match a 'CancellationException'
suspend fun compute() {
    try {
        delay(100)
    } catch (<caret>e: IOException) {
    }
}
