// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// `coroutineScope` is a suspend call. It can raise the 'CancellationException' of the caller.
suspend fun compute(): Int? {
    return <caret>runCatching {
        coroutineScope {
            delay(100)
            42
        }
    }.getOrNull()
}
