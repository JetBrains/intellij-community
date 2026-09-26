// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun compute(): Int {
    return <caret>runCatching {
        delay(100)
        42
    }.getOrElse {
        if (it is CancellationException) throw it
        0
    }
}
