// WITH_COROUTINES
// PROBLEM: 'runCatching' suppresses 'CancellationException' and breaks coroutine cancellation
// FIX: Check for cancellation in 'onFailure { ... }'
package test

import kotlin.runCatching as tryIt
import kotlinx.coroutines.delay

suspend fun compute(): Int? {
    return <caret>tryIt {
        delay(100)
        42
    }.getOrNull()
}
