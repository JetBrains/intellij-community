// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

fun CoroutineScope.compute() {
    launch {
        try {
            delay(100)
        } catch (<caret>e: Exception) {
            coroutineContext.ensureActive()
        }
    }
}
