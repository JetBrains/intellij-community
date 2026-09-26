// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

// 'ensureActive' is also available on the 'Job' itself
suspend fun compute(): Int {
    val result = <caret>runCatching {
        delay(100)
        42
    }.getOrElse { 0 }
    currentCoroutineContext().job.ensureActive()
    return result
}
