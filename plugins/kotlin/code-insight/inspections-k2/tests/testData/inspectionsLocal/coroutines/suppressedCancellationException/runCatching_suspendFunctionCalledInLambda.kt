// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlinx.coroutines.delay

suspend fun fetch(): Int {
    delay(100)
    return 42
}

suspend fun compute(): Int? {
    return <caret>runCatching { fetch() }.getOrNull()
}
