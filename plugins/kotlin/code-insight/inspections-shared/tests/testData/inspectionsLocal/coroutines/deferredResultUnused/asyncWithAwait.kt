// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

suspend fun CoroutineScope.asyncWithAwait() {
    // Should not be flagged - result is used
    <caret>async { 3 }.await()
}