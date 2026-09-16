// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// Nothing inside the block can throw a 'CancellationException'
suspend fun compute(raw: String): Int? {
    delay(100)
    return <caret>runCatching {
        raw.toInt()
    }.getOrNull()
}
