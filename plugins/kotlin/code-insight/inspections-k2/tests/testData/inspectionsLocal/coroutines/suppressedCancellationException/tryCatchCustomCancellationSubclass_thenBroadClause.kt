// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class LoadCancelled : CancellationException("load cancelled")

// Technically, a CE can be suppressed here because a regular CE will be swallowed.
// However, in this case we assume that the user is aware of the risk and disable the inspection.
suspend fun compute(): Int {
    return try {
        delay(100)
        42
    } catch (e: LoadCancelled) {
        -1
    } catch (<caret>e: Exception) {
        0
    }
}
