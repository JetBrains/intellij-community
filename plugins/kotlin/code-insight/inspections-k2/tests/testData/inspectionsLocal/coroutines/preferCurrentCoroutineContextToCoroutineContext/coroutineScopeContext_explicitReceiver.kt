// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.CoroutineScope

suspend fun test(scope: CoroutineScope) {
    scope.<caret>coroutineContext
}