// WITH_COROUTINES
// PROBLEM: Suspicious implicit 'CoroutineScope' receiver access in suspending context
// FIX: Add explicit labeled receiver (does not change semantics)
package test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

private suspend fun suspendWrapper(action: suspend () -> Unit) {
    action()
}

fun CoroutineScope.doStuff() {}

suspend fun CoroutineScope.test() {
    val deferred = async { 42 }

    suspendWrapper {
        select<Unit> {
            deferred.onAwait {
                <caret>doStuff()
            }
        }
    }
}