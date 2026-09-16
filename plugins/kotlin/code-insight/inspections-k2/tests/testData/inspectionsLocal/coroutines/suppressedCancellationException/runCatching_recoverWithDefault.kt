// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.delay

// This suppresses the cancellation exception, but we do not report it.
// The reason is that the recover swallows the exception, but it might also
// handle it in a non-trivial way. To be cautious, we disable the inspection here.
suspend fun compute(): Int {
    return <caret>runCatching {
        delay(100)
        42
    }.recover { 0 }.getOrThrow()
}
