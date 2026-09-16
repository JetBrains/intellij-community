// WITH_COROUTINES
// PROBLEM: none
package test

import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay

suspend fun compute(): Int {
    return try {
        delay(100)
        42
    } catch (e: CancellationException) {
        throw e
    } catch (<caret>e: Exception) {
        0
    }
}
