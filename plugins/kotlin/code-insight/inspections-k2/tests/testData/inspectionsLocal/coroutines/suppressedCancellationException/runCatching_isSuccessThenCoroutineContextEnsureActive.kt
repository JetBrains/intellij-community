// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

fun CoroutineScope.compute() {
    launch {
        val ok = <caret>runCatching {
            delay(100)
        }.isSuccess
        coroutineContext.ensureActive()
        println(ok)
    }
}
